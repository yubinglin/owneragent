package com.owneragent.agent.react;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 主聊天图的入口节点：每轮对话开始时在 {@code greeting} 通道写入问候语。
 *
 * <p>原来的示例把 "Hello from GreeterNode!" 当作字符串塞进 {@code messages}；
 * 接进主聊天图后 {@code messages} 里必须是 Spring AI 的 {@link org.springframework.ai.chat.messages.Message}，
 * 塞字符串会污染发给模型的对话历史，因此改为写入独立的 {@code greeting} 通道。</p>
 */
public class GreeterNode implements NodeAction<SimpleState> {

    private static final Logger log = LoggerFactory.getLogger(GreeterNode.class);

    public static final String GREETING = "Hello from GreeterNode!";

    @Override
    public Map<String, Object> apply(SimpleState state) {
        log.info("GreeterNode executing. messages={}", state.messages().size());
        return Map.of(SimpleState.GREETING_KEY, GREETING);
    }
}
