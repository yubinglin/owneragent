package com.owneragent.agent.react;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import com.owneragent.agent.web.AgentStep;
import com.owneragent.agent.web.ChatReply;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

@Service
public class AgentService {

    private static final Pattern THREAD_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private final CompiledGraph<SimpleState> graph;

    /**
     * MemorySaver 的单次 get/put 有锁，但一整轮 graph run 不是原子的。
     * demo 场景直接用全局锁串行执行；后续可以按 threadId 分段加锁。
     */
    private final ReentrantLock runLock = new ReentrantLock();

    public AgentService(CompiledGraph<SimpleState> graph) {
        this.graph = graph;
    }

    public ChatReply chat(String threadId, String message) {
        String resolvedThreadId = resolveThreadId(threadId);
        String userMessage = validateMessage(message);
        runLock.lock();
        try {
            SimpleState state = runGraph(resolvedThreadId, userMessage);
            return new ChatReply(resolvedThreadId, extractReply(state), extractSteps(state));
        } finally {
            runLock.unlock();
        }
    }

    private SimpleState runGraph(String threadId, String message) {
        var input = GraphInput.args(Map.of(
                SimpleState.MESSAGES_STATE, new UserMessage(message)));
        var config = RunnableConfig.builder().threadId(threadId).build();

        var stream = graph.stream(input, config);
        try {
            return stream.stream()
                    .reduce((previous, current) -> current)
                    .map(NodeOutput::state)
                    .orElseThrow(() -> new IllegalStateException("Agent 图没有产生任何输出"));
        } finally {
            // async-generator 5.1.0 的 cancel(false) 不会关闭非 daemon executor，会导致 JVM 挂住。
            stream.cancel(true);
        }
    }

    private static String resolveThreadId(String threadId) {
        if (threadId == null || threadId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = threadId.trim();
        if (!THREAD_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("threadId 只能包含字母、数字、下划线或短横线，长度 1-64");
        }
        return trimmed;
    }

    private static String validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message 长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static String extractReply(SimpleState state) {
        List<Message> messages = state.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof AssistantMessage assistant
                    && assistant.getText() != null
                    && !assistant.getText().isBlank()) {
                return assistant.getText();
            }
        }
        return "";
    }

    /**
     * 只返回本轮（最后一条 UserMessage 之后）发生的工具调用与结果。
     */
    private static List<AgentStep> extractSteps(SimpleState state) {
        List<Message> messages = state.messages();
        int start = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                start = i + 1;
                break;
            }
        }

        List<AgentStep> steps = new ArrayList<>();
        for (int i = start; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistant) {
                if (assistant.getToolCalls() != null) {
                    for (AssistantMessage.ToolCall toolCall : assistant.getToolCalls()) {
                        steps.add(new AgentStep("tool_call", toolCall.name(), toolCall.arguments()));
                    }
                }
            } else if (message instanceof ToolResponseMessage toolResponseMessage) {
                toolResponseMessage.getResponses()
                        .forEach(response -> steps.add(
                                new AgentStep("tool_result", response.name(), response.responseData())));
            }
        }
        return steps;
    }
}
