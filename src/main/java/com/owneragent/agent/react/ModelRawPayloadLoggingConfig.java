package com.owneragent.agent.react;

import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把原始报文日志拦截器挂到自动配置的 {@code RestClient.Builder} 上。
 * DeepSeek 的自动配置会取这个 Builder 来创建 HTTP 客户端，因此模型调用会被拦截。
 */
@Configuration
public class ModelRawPayloadLoggingConfig {

    @Bean
    RestClientCustomizer modelRawPayloadLoggingCustomizer() {
        return builder -> builder.requestInterceptor(new ModelRawPayloadLoggingInterceptor());
    }
}
