package com.owneragent.agent;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import com.owneragent.agent.examples.DraftReviewGraph;
import com.owneragent.agent.examples.SupportTriageGraph;
import com.owneragent.agent.config.AgentConfig;
import com.owneragent.agent.springai.DemoTools;
import com.owneragent.agent.react.SimpleState;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Objects;

/**
 * 把工程里的三张图暴露给 LangGraph4j Studio。
 *
 * <ul>
 *   <li>{@code owneragent}：主聊天 ReAct 图（Spring AI + DeepSeek）；</li>
 *   <li>{@code support-triage}：Builder 示例「工单分流」，输入 {@code ticket}；</li>
 *   <li>{@code draft-review-loop}：Builder 示例「草稿评审」，输入 {@code topic} 和可选的
 *       {@code maxRounds}。</li>
 * </ul>
 *
 * <p>Studio 是开发期工具，提供图结构展示、节点输出查看、按 thread 重跑等调试能力。
 * 它通过 {@code /init} 返回图定义，通过 {@code /stream/*} 执行图；UI 静态资源由
 * {@code langgraph4j-studio-springboot} jar 提供。</p>
 *
 * <p>本工程自己的聊天页占用了 {@code static/index.html}，会遮住 jar 里的 Studio 首页，
 * 因此仓库里另放了一份 {@code static/studio.html}（内容与 jar 内的 index.html 相同）。
 * 访问地址形如 {@code http://localhost:18081/studio.html?instance=support-triage}。</p>
 */
@Configuration
public class StudioConfig extends LangGraphStudioConfig {

    /** 主聊天图的 instance id。 */
    public static final String INSTANCE_ID = "owneragent";
    /** Builder 示例「工单分流」的 instance id。 */
    public static final String SUPPORT_TRIAGE_ID = "support-triage";
    /** Builder 示例「草稿评审循环」的 instance id。 */
    public static final String DRAFT_REVIEW_ID = "draft-review-loop";

    private final StateGraph<SimpleState> graph;
    private final StateGraph<SupportTriageGraph.TriageState> supportTriageGraph;
    private final StateGraph<DraftReviewGraph.ReviewState> draftReviewGraph;

    public StudioConfig(ChatModel chatModel, DemoTools demoTools) throws GraphStateException {
        // 与 AgentConfig.agentGraph 共用同一套构图逻辑，但这里保留未编译的 StateGraph，
        // Studio 会按会话用自己的 CompileConfig 编译。
        this.graph = AgentConfig.agentStateGraph(chatModel, demoTools);
        this.supportTriageGraph = SupportTriageGraph.stateGraph();
        this.draftReviewGraph = DraftReviewGraph.stateGraph();
    }

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        return Map.of(
                INSTANCE_ID, LangGraphStudioServer.Instance.builder()
                        .title("OwnerAgent ReAct")
                        // Studio 的输入表单只提供字符串；这里在进图前转成 Spring AI 的 UserMessage。
                        // 参数名必须与 SimpleState 的 messages 通道一致。
                        .addInputStringArg(SimpleState.MESSAGES_STATE, true,
                                value -> new UserMessage(Objects.toString(value)))
                        .graph(graph)
                        .compileConfig(studioCompileConfig())
                        .build(),

                SUPPORT_TRIAGE_ID, LangGraphStudioServer.Instance.builder()
                        .title("示例：工单分流（Builder）")
                        .addInputStringArg(SupportTriageGraph.TICKET, true)
                        .graph(supportTriageGraph)
                        .compileConfig(studioCompileConfig())
                        .build(),

                DRAFT_REVIEW_ID, LangGraphStudioServer.Instance.builder()
                        .title("示例：草稿评审循环（Builder）")
                        .addInputStringArg(DraftReviewGraph.TOPIC, true)
                        .addInputStringArg(DraftReviewGraph.MAX_ROUNDS, false, StudioConfig::parseMaxRounds)
                        .graph(draftReviewGraph)
                        .compileConfig(studioCompileConfig())
                        .build());
    }

    /**
     * 每个 Studio 实例使用独立的 checkpoint saver，避免不同图之间共享 thread 状态。
     * {@code releaseThread(false)} 保证图跑到 END 后仍保留 checkpoint，Studio 才能回看/恢复执行。
     */
    private static CompileConfig studioCompileConfig() {
        return CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .releaseThread(false)
                .build();
    }

    /**
     * Studio 的输入都是字符串；{@code maxRounds} 留空时回退到图里的默认值。
     */
    static Object parseMaxRounds(Object value) {
        String text = Objects.toString(value, "").trim();
        if (text.isEmpty()) {
            return DraftReviewGraph.DEFAULT_MAX_ROUNDS;
        }
        try {
            return Math.max(1, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("maxRounds 必须是正整数，实际为: " + text);
        }
    }
}
