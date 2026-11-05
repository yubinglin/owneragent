package com.owneragent.agent.config;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

/**
 * DeepSeek 返回带 tool_calls 的 assistant 消息时，content 可能是 null。
 * LangGraph4j 的 checkpoint 序列化器反序列化时会把 null 读成字面量字符串 "null"，
 * 下一轮就会把 "null" 当作历史回复发给模型。
 * 这里在图之外把这类消息的 content 统一成空字符串，保证 checkpoint 往返无损。
 */
final class ToolCallContentNormalizingChatModel implements ChatModel {

    private final ChatModel delegate;

    ToolCallContentNormalizingChatModel(ChatModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse response = delegate.call(prompt);
        if (response == null) {
            return null;
        }
        List<Generation> results = response.getResults().stream()
                .map(ToolCallContentNormalizingChatModel::normalize)
                .toList();
        return new ChatResponse(results, response.getMetadata());
    }

    private static Generation normalize(Generation generation) {
        AssistantMessage message = generation.getOutput();
        if (message == null || message.getText() != null || !message.hasToolCalls()) {
            return generation;
        }
        return new Generation(message.mutate().content("").build(), generation.getMetadata());
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }
}
