package com.owneragent.agent.react;

import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 主聊天图的收尾节点：整轮对话（含工具调用循环）结束后，
 * 根据 {@code greeting} 通道是否被 {@link GreeterNode} 写入，在 {@code acknowledged} 通道记录确认语。
 *
 * <p>确认语同样不放进 {@code messages}，避免覆盖模型真正的最终回复。</p>
 */
public class ResponderNode implements NodeAction<SimpleState> {

    private static final Logger log = LoggerFactory.getLogger(ResponderNode.class);

    public static final String ACKNOWLEDGED = "Acknowledged greeting!";
    public static final String NO_GREETING = "No greeting found.";

    @Override
    public Map<String, Object> apply(SimpleState state) {
        String acknowledged = GreeterNode.GREETING.equals(state.greeting())
                ? ACKNOWLEDGED
                : NO_GREETING;
        log.info("ResponderNode executing. greeting={}, acknowledged={}", state.greeting(), acknowledged);
        return Map.of(SimpleState.ACKNOWLEDGED_KEY, acknowledged);
    }
}
