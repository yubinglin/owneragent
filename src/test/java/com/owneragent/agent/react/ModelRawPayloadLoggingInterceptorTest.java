package com.owneragent.agent.react;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRawPayloadLoggingInterceptorTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ModelRawPayloadLoggingInterceptor.class);
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
    void logsRawRequestAndResponseBodies() throws Exception {
        ModelRawPayloadLoggingInterceptor interceptor = new ModelRawPayloadLoggingInterceptor();
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST,
                URI.create("https://api.deepseek.com/chat/completions"));
        byte[] requestBody = "{\"model\":\"deepseek-chat\"}".getBytes(StandardCharsets.UTF_8);
        MockClientHttpResponse response = new MockClientHttpResponse(
                "{\"id\":\"chat-1\"}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);

        ClientHttpResponse result = interceptor.intercept(request, requestBody, (req, body) -> response);

        // 响应体被拦截器读过一次后，仍要能被 RestClient 再读一次
        assertEquals("{\"id\":\"chat-1\"}",
                new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("{\"id\":\"chat-1\"}",
                new String(result.getBody().readAllBytes(), StandardCharsets.UTF_8));

        String logs = capturedLogs();
        assertTrue(logs.contains("[模型原始请求] POST https://api.deepseek.com/chat/completions"),
                "缺少原始请求行: " + logs);
        assertTrue(logs.contains("{\"model\":\"deepseek-chat\"}"), "缺少原始请求体: " + logs);
        assertTrue(logs.contains("[模型原始响应] status=200"), "缺少原始响应行: " + logs);
        assertTrue(logs.contains("{\"id\":\"chat-1\"}"), "缺少原始响应体: " + logs);
    }

    private String capturedLogs() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
