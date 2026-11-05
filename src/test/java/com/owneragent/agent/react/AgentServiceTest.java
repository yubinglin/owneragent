package com.owneragent.agent.react;

import com.owneragent.agent.config.AgentConfig;
import com.owneragent.agent.springai.DemoTools;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import com.owneragent.agent.web.ChatReply;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentServiceTest {

    private static AgentService serviceWith(ScriptedChatModel model) throws GraphStateException {
        CompiledGraph<SimpleState> graph =
                new AgentConfig().agentGraph(model, new DemoTools());
        return new AgentService(graph);
    }

    @Test
    void executesToolAndReturnsFinalReply() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenToolCall("call-1", "calculate", "{\"expression\":\"(12+8)*3\"}")
                .thenReply("计算结果是 60。");

        ChatReply reply = serviceWith(model).chat("tool-thread", "帮我算一下 (12+8)*3");

        assertEquals("tool-thread", reply.threadId());
        assertEquals("计算结果是 60。", reply.reply());
        assertEquals(2, reply.steps().size());
        assertEquals("tool_call", reply.steps().get(0).type());
        assertEquals("calculate", reply.steps().get(0).name());
        assertEquals("tool_result", reply.steps().get(1).type());
        assertEquals("calculate", reply.steps().get(1).name());
        assertTrue(reply.steps().get(1).detail().contains("= 60"),
                "工具结果应包含计算结果，实际为: " + reply.steps().get(1).detail());

        ChatOptions options = model.prompts().get(0).getOptions();
        ToolCallingChatOptions toolOptions = assertInstanceOf(ToolCallingChatOptions.class, options);
        assertEquals(3, toolOptions.getToolCallbacks().size(), "三个 @Tool 方法都应挂到 Prompt 上");
    }

    @Test
    void customGreeterAndResponderParticipateInMainGraph() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel().thenReply("你好，我是 OwnerAgent。");
        CompiledGraph<SimpleState> graph = new AgentConfig().agentGraph(model, new DemoTools());

        var stream = graph.stream(
                GraphInput.args(Map.of(SimpleState.MESSAGES_STATE, new UserMessage("你好"))),
                RunnableConfig.builder().threadId("custom-nodes").build());
        SimpleState state;
        try {
            state = stream.stream()
                    .reduce((previous, current) -> current)
                    .map(NodeOutput::state)
                    .orElseThrow();
        } finally {
            stream.cancel(true);
        }

        assertEquals(GreeterNode.GREETING, state.greeting(), "GreeterNode 应写入 greeting 通道");
        assertEquals(ResponderNode.ACKNOWLEDGED, state.acknowledged(), "ResponderNode 应写入 acknowledged 通道");
        assertEquals("你好，我是 OwnerAgent。", state.lastMessage().orElseThrow().getText(),
                "自定义节点不应往 messages 里塞内容，最后一条消息仍是模型回复");
    }

    @Test
    void keepsMemoryAcrossTurnsOnSameThread() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenReply("记住了，你叫小林。")
                .thenReply("你叫小林。");

        AgentService service = serviceWith(model);
        ChatReply first = service.chat("memory-thread", "我叫小林。");
        ChatReply second = service.chat("memory-thread", "我叫什么？");

        assertEquals("记住了，你叫小林。", first.reply());
        assertEquals("你叫小林。", second.reply());

        Prompt secondPrompt = model.prompts().get(1);
        assertTrue(hasUserText(secondPrompt, "我叫小林。"), "第二轮 Prompt 应包含第一轮用户消息");
        assertTrue(hasAssistantText(secondPrompt, "记住了，你叫小林。"), "第二轮 Prompt 应包含第一轮回复");
    }

    @Test
    void keepsDuplicateUserMessages() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenReply("收到。")
                .thenReply("又收到。");

        AgentService service = serviceWith(model);
        service.chat("dup-thread", "同一句话");
        service.chat("dup-thread", "同一句话");

        long count = model.prompts().get(1).getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .filter(message -> "同一句话".equals(message.getText()))
                .count();
        assertEquals(2, count, "相同文本不能被 appender 去重");
    }

    @Test
    void preservesEmptyToolCallContentAcrossCheckpoint() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenToolCall("call-empty", "calculate", "{\"expression\":\"2+2\"}")
                .thenReply("等于 4。")
                .thenReply("不客气。");

        AgentService service = serviceWith(model);
        service.chat("checkpoint-empty", "算一下 2+2");
        service.chat("checkpoint-empty", "谢谢");

        Prompt secondPrompt = model.prompts().get(2);
        assertFalse(hasLiteralNullText(secondPrompt), "checkpoint 往返后出现了字面量 \"null\" 消息");
        assertTrue(countToolCallMessages(secondPrompt) > 0, "tool-call 消息在 checkpoint 往返后丢失");
    }

    @Test
    void preservesNullToolCallContentAcrossCheckpoint() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenToolCallWithNullContent("call-null", "calculate", "{\"expression\":\"1+1\"}")
                .thenReply("等于 2。")
                .thenReply("好的。");

        AgentService service = serviceWith(model);
        service.chat("checkpoint-null", "算一下 1+1");
        service.chat("checkpoint-null", "谢谢");

        Prompt secondPrompt = model.prompts().get(2);
        assertFalse(hasLiteralNullText(secondPrompt), "null 内容被反序列化成了字面量 \"null\"");
        assertTrue(countToolCallMessages(secondPrompt) > 0, "tool-call 消息在 checkpoint 往返后丢失");
    }

    private static boolean hasUserText(Prompt prompt, String text) {
        return prompt.getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .anyMatch(message -> text.equals(message.getText()));
    }

    private static boolean hasAssistantText(Prompt prompt, String text) {
        return prompt.getInstructions().stream()
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .anyMatch(message -> text.equals(message.getText()));
    }

    private static boolean hasLiteralNullText(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        return messages.stream().anyMatch(message -> "null".equals(message.getText()));
    }

    private static long countToolCallMessages(Prompt prompt) {
        return prompt.getInstructions().stream()
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .filter(AssistantMessage::hasToolCalls)
                .count();
    }
}
