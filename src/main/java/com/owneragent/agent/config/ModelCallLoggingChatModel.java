package com.owneragent.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ChatModel 装饰器：每次模型调用前后各打一条日志，记录入参（模型参数、消息列表）
 * 和出参（文本、tool_calls、finishReason、token 用量、耗时）。
 *
 * <p>LangGraph4j 的 AgentExecutor 直接持有 ChatModel、不经过 ChatClient，
 * 所以 ChatClient 的 Advisor 拦截不到，只能在模型外面包一层。</p>
 */
final class ModelCallLoggingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(ModelCallLoggingChatModel.class);

    private final ChatModel delegate;

    ModelCallLoggingChatModel(ChatModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        logRequest(prompt);

        long startNanos = System.nanoTime();
        try {
            ChatResponse response = delegate.call(prompt);
            logResponse(response, elapsedMillis(startNanos));
            return response;
        } catch (RuntimeException ex) {
            log.error("[模型响应] 调用失败，耗时 {} ms，异常: {}",
                    elapsedMillis(startNanos), ex.toString(), ex);
            throw ex;
        }
    }

    @Override
    public ChatOptions getOptions() {
        return delegate.getOptions();
    }

    private void logRequest(Prompt prompt) {
        ChatOptions options = prompt.getOptions() != null ? prompt.getOptions() : delegate.getOptions();
        int toolCount = (options instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null)
                ? toolOptions.getToolCallbacks().size()
                : 0;

        StringBuilder line = new StringBuilder("[模型请求]")
                .append(" model=").append(nullToDash(options != null ? options.getModel() : null))
                .append(", temperature=").append(nullToDash(options != null ? options.getTemperature() : null))
                .append(", maxTokens=").append(nullToDash(options != null ? options.getMaxTokens() : null))
                .append(", tools=").append(toolCount)
                .append(", messages=").append(prompt.getInstructions().size());

        for (Message message : prompt.getInstructions()) {
            line.append(System.lineSeparator()).append("    ").append(formatMessage(message));
        }
        log.info(line.toString());
    }

    private static void logResponse(ChatResponse response, long elapsedMillis) {
        if (response == null) {
            log.info("[模型响应] 耗时 {} ms，模型返回 null", elapsedMillis);
            return;
        }

        StringBuilder line = new StringBuilder("[模型响应]")
                .append(" 耗时=").append(elapsedMillis).append(" ms")
                .append(", model=").append(nullToDash(response.getMetadata().getModel()))
                .append(", tokens=").append(formatUsage(response.getMetadata().getUsage()));

        for (Generation generation : response.getResults()) {
            AssistantMessage message = generation.getOutput();
            line.append(System.lineSeparator())
                    .append("    finishReason=")
                    .append(nullToDash(generation.getMetadata().getFinishReason()))
                    .append(", text=").append(oneLine(message.getText()));
            if (message.hasToolCalls()) {
                line.append(", toolCalls=").append(formatToolCalls(message));
            }
        }
        log.info(line.toString());
    }

    private static String formatMessage(Message message) {
        String role = message.getMessageType().getValue();
        if (message instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
            return "[" + role + "] " + oneLine(assistant.getText())
                    + " toolCalls=" + formatToolCalls(assistant);
        }
        if (message instanceof ToolResponseMessage toolResponse) {
            return "[" + role + "] " + toolResponse.getResponses().stream()
                    .map(response -> response.name() + "=" + oneLine(response.responseData()))
                    .collect(Collectors.joining(", "));
        }
        return "[" + role + "] " + oneLine(message.getText());
    }

    private static String formatToolCalls(AssistantMessage message) {
        return message.getToolCalls().stream()
                .map(toolCall -> toolCall.name() + "(" + toolCall.arguments() + ")")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private static String formatUsage(Usage usage) {
        if (usage == null) {
            return "-";
        }
        return "prompt=" + nullToDash(usage.getPromptTokens())
                + ", completion=" + nullToDash(usage.getCompletionTokens())
                + ", total=" + nullToDash(usage.getTotalTokens());
    }

    /** 消息内容可能带换行，压成一行避免一条日志被拆散。 */
    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String nullToDash(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
