package com.owneragent.agent.react;

import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 主聊天图的状态。
 *
 * <p>在 {@link MessagesState} 的基础上多了两个自定义标量通道：</p>
 * <ul>
 *   <li>{@value #GREETING_KEY}：{@link GreeterNode} 在每轮对话开始时写入的问候语；</li>
 *   <li>{@value #ACKNOWLEDGED_KEY}：{@link ResponderNode} 在整轮对话结束时写入的确认语。</li>
 * </ul>
 *
 * <p>这两个通道不放进 {@code messages}，因此不会作为对话历史发给模型，
 * 也不会干扰“最后一条 AssistantMessage 就是最终回复”的提取逻辑。</p>
 */
public class SimpleState extends MessagesState<Message> {

    public static final String GREETING_KEY = "greeting";
    public static final String ACKNOWLEDGED_KEY = "acknowledged";

    public static final Map<String, Channel<?>> SCHEMA;

    static {
        Map<String, Channel<?>> schema = new HashMap<>(MessagesState.SCHEMA);
        // 默认的 appender 会把完全相同的消息去重；改用 appenderWithDuplicate
        // 保证用户重复发送同一句话时，两条 UserMessage 都进入历史。
        schema.put(MESSAGES_STATE, Channels.appenderWithDuplicate(ArrayList::new));
        schema.put(GREETING_KEY, Channels.base(() -> ""));
        schema.put(ACKNOWLEDGED_KEY, Channels.base(() -> ""));
        SCHEMA = Map.copyOf(schema);
    }

    public SimpleState(Map<String, Object> initData) {
        super(initData);
    }

    public String greeting() {
        return this.<String>value(GREETING_KEY).orElse("");
    }

    public String acknowledged() {
        return this.<String>value(ACKNOWLEDGED_KEY).orElse("");
    }
}
