package org.bsc.langgraph4j.gen;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 Builder 生成的 {@link AgentWorkflowBuilder} 实现一个可运行的工单分流示例。
 *
 * <p>相比生成的占位测试，这里补上了：</p>
 * <ul>
 *   <li>自定义状态 {@link TriageState}（ticket / category / reply 三个通道）；</li>
 *   <li>classify 节点的真实分类逻辑；</li>
 *   <li>route 条件边按分类结果返回 {@code auto_reply} 或 {@code escalate}；</li>
 *   <li>对两条分支结果的断言。</li>
 * </ul>
 */
class SupportTriageDemoTest {

    static class TriageState extends AgentState {

        static final String TICKET = "ticket";
        static final String CATEGORY = "category";
        static final String REPLY = "reply";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TICKET, Channels.base(() -> ""),
                CATEGORY, Channels.base(() -> ""),
                REPLY, Channels.base(() -> "")
        );

        TriageState(Map<String, Object> initData) {
            super(initData);
        }

        String ticket() {
            return this.<String>value(TICKET).orElse("");
        }

        String category() {
            return this.<String>value(CATEGORY).orElse("");
        }

        String reply() {
            return this.<String>value(REPLY).orElse("");
        }
    }

    private CompiledGraph<TriageState> triageGraph() throws Exception {
        var stateGraph = new AgentWorkflowBuilder<TriageState>()
                .classify(node_async(state -> {
                    var ticket = state.ticket();
                    var sensitive = ticket.contains("退款") || ticket.contains("投诉");
                    return Map.of(TriageState.CATEGORY, sensitive ? "human" : "auto");
                }))
                .auto_reply(node_async(state -> Map.of(
                        TriageState.REPLY,
                        "已自动回复：我们会尽快处理「" + state.ticket() + "」")))
                .escalate(node_async(state -> Map.of(
                        TriageState.REPLY,
                        "已转人工：工单「" + state.ticket() + "」需要客服跟进")))
                .route(edge_async(state ->
                        "human".equals(state.category()) ? "escalate" : "auto_reply"))
                .build(TriageState::new, TriageState.SCHEMA);

        return stateGraph.compile(CompileConfig.builder().build());
    }

    @Test
    void routes_normal_ticket_to_auto_reply() throws Exception {
        var result = triageGraph()
                .invoke(GraphInput.args(Map.of(TriageState.TICKET, "帮我改一下收货地址")), RunnableConfig.empty())
                .orElseThrow();

        assertEquals("auto", result.category());
        assertTrue(result.reply().startsWith("已自动回复"), result.reply());
        System.out.println("普通工单 -> " + result.category() + " / " + result.reply());
    }

    @Test
    void routes_sensitive_ticket_to_human() throws Exception {
        var result = triageGraph()
                .invoke(GraphInput.args(Map.of(TriageState.TICKET, "我要投诉并申请退款")), RunnableConfig.empty())
                .orElseThrow();

        assertEquals("human", result.category());
        assertTrue(result.reply().startsWith("已转人工"), result.reply());
        System.out.println("敏感工单 -> " + result.category() + " / " + result.reply());
    }
}
