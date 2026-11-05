package com.owneragent.agent.react;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.owneragent.agent.config.ModelCallLoggingChatModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCallLoggingChatModelTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ModelCallLoggingChatModel.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsRequestInputAndResponseOutput() {
        ScriptedChatModel delegate = new ScriptedChatModel()
                .thenToolCall("call-1", "calculate", "{\"expression\":\"1+1\"}");
        ModelCallLoggingChatModel model = new ModelCallLoggingChatModel(delegate);

        ChatResponse response = model.call(new Prompt("算一下 1+1"));

        assertNotNull(response);
        assertTrue(response.hasToolCalls());

        String logs = capturedLogs();
        assertTrue(logs.contains("[模型请求]"), "缺少请求日志: " + logs);
        assertTrue(logs.contains("messages=1"), "请求日志应包含消息数量: " + logs);
        assertTrue(logs.contains("[user] 算一下 1+1"), "请求日志应包含用户输入: " + logs);
        assertTrue(logs.contains("[模型响应]"), "缺少响应日志: " + logs);
        assertTrue(logs.contains("toolCalls=[calculate({\"expression\":\"1+1\"})]"),
                "响应日志应包含 tool_calls: " + logs);
        assertTrue(logs.contains("tokens=prompt=0, completion=0, total=0"),
                "响应日志应包含 token 用量: " + logs);
    }

    @Test
    void logsFailureAndRethrows() {
        ModelCallLoggingChatModel model = new ModelCallLoggingChatModel(new ScriptedChatModel());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> model.call(new Prompt("你好")));

        String logs = capturedLogs();
        assertTrue(logs.contains("调用失败"), "失败也应记录日志: " + logs);
        assertTrue(logs.contains(ex.getMessage()), "失败日志应包含异常信息: " + logs);
    }

    private String capturedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
