package com.owneragent.agent.react;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 在 HTTP 层记录真正发给模型的原始 JSON 报文（请求体与响应体）。
 *
 * <p>Spring AI 的 DeepSeek 集成底层用 {@code RestClient} 发请求，
 * 挂一个拦截器就能拿到序列化后的完整报文（含 messages、tools、temperature 等）。
 * 只记录报文，不记录请求头，避免把 Authorization 里的 API Key 打进日志。</p>
 */
final class ModelRawPayloadLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ModelRawPayloadLoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {

        log.info("[模型原始请求] {} {}\n{}", request.getMethod(), request.getURI(), decode(body));

        ClientHttpResponse response = execution.execute(request, body);
        byte[] responseBody;
        try {
            responseBody = response.getBody().readAllBytes();
        } catch (IOException | RuntimeException ex) {
            response.close();
            throw ex;
        }

        log.info("[模型原始响应] status={} {}\n{}",
                response.getStatusCode().value(), response.getStatusText(), decode(responseBody));

        // 响应体已经被读走，包一层缓存，保证 RestClient 后续还能正常解析。
        return new CachedBodyResponse(response, responseBody);
    }

    private static String decode(byte[] body) {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    private static final class CachedBodyResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;

        private final byte[] body;

        private CachedBodyResponse(ClientHttpResponse delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
