package com.owneragent.agent.react;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 测试用的脚本化 ChatModel，不访问网络。
 *
 * <p>{@code getOptions()} 必须返回 {@code ToolCallingChatOptions}，
 * 否则 LangGraph4j 的 DefaultChatService 不会把工具回调挂到 Prompt 上。</p>
 */
public class ScriptedChatModel implements ChatModel {

    private final Deque<Function<Prompt, ChatResponse>> script = new ArrayDeque<>();
    private final List<Prompt> prompts = new ArrayList<>();

    /** 下一次模型调用直接回复一段文本。 */
    public ScriptedChatModel thenReply(String text) {
        return then(prompt -> response(new AssistantMessage(text)));
    }

    /** 下一次模型调用返回一个 tool_call，content 为空字符串（DeepSeek 的常见形态）。 */
    public ScriptedChatModel thenToolCall(String toolCallId, String name, String arguments) {
        return thenToolCall(toolCallId, name, arguments, "");
    }

    /** 下一次模型调用返回一个 tool_call，content 为指定值。 */
    public ScriptedChatModel thenToolCall(String toolCallId, String name, String arguments, String content) {
        AssistantMessage message = AssistantMessage.builder()
                .content(content)
                .toolCalls(List.of(new AssistantMessage.ToolCall(toolCallId, "function", name, arguments)))
                .build();
        return then(prompt -> response(message));
    }

    /** 下一次模型调用返回一个 content 为 null 的 tool_call。 */
    public ScriptedChatModel thenToolCallWithNullContent(String toolCallId, String name, String arguments) {
        AssistantMessage message = AssistantMessage.builder()
                .content(null)
                .toolCalls(List.of(new AssistantMessage.ToolCall(toolCallId, "function", name, arguments)))
                .build();
        return then(prompt -> response(message));
    }

    /** 下一次模型调用先执行断言，再返回文本回复。 */
    public ScriptedChatModel thenAsserting(Consumer<Prompt> assertion, String reply) {
        return then(prompt -> {
            assertion.accept(prompt);
            return response(new AssistantMessage(reply));
        });
    }

    public ScriptedChatModel then(Function<Prompt, ChatResponse> step) {
        script.add(step);
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        prompts.add(prompt);
        Function<Prompt, ChatResponse> step = script.poll();
        if (step == null) {
            throw new IllegalStateException("ScriptedChatModel 的脚本已耗尽，实际调用次数: " + prompts.size());
        }
        return step.apply(prompt);
    }

    @Override
    public ChatOptions getOptions() {
        return DeepSeekChatOptions.builder().build();
    }

    public List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public static ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }
}
