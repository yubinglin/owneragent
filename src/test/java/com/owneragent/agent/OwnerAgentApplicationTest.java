package com.owneragent.agent;

import com.owneragent.agent.react.ScriptedChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 验证 Spring 上下文（含 DeepSeek 自动配置、AgentConfig、Web 层）能完整启动。
 * 用假模型避免真实网络调用。
 */
@SpringBootTest(properties = "spring.ai.deepseek.api-key=test-key")
class OwnerAgentApplicationTest {

    @TestConfiguration
    static class FakeChatModelConfig {
        @Bean
        @Primary
        ChatModel fakeChatModel() {
            return new ScriptedChatModel();
        }
    }

    @Test
    void contextLoads() {
    }
}
