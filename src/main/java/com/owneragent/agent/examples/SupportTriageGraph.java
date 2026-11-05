package com.owneragent.agent.examples;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Builder 示例「工单分流」的可运行版本，不依赖 Spring AI，可直接注册到 Studio。
 *
 * <pre>
 * START → classify ──route──┬─ auto_reply → END
 *                            └─ escalate   → END
 * </pre>
 *
 * <p>与 {@code builder-examples/support-triage} 中的示例逻辑一致：
 * 包含「退款」「投诉」的工单转人工，其余走自动回复。</p>
 */
public final class SupportTriageGraph {

    public static final String TICKET = "ticket";
    public static final String CATEGORY = "category";
    public static final String REPLY = "reply";

    private SupportTriageGraph() {
    }

    public static StateGraph<TriageState> stateGraph() throws GraphStateException {
        return new StateGraph<>(TriageState.SCHEMA, TriageState::new)
                .addNode("classify", node_async(state -> {
                    String ticket = state.ticket();
                    boolean sensitive = ticket.contains("退款") || ticket.contains("投诉");
                    return Map.of(CATEGORY, sensitive ? "human" : "auto");
                }))
                .addNode("auto_reply", node_async(state -> Map.of(
                        REPLY, "已自动回复：我们会尽快处理「" + state.ticket() + "」")))
                .addNode("escalate", node_async(state -> Map.of(
                        REPLY, "已转人工：工单「" + state.ticket() + "」需要客服跟进")))
                .addEdge(START, "classify")
                .addConditionalEdges("classify",
                        edge_async(state -> state.category()),
                        EdgeMappings.builder()
                                .to("auto_reply", "auto")
                                .to("escalate", "human")
                                .build())
                .addEdge("auto_reply", END)
                .addEdge("escalate", END);
    }

    /**
     * 工单分流状态：三张单值通道，输入只需 {@code ticket}。
     */
    public static final class TriageState extends AgentState {

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TICKET, Channels.base(() -> ""),
                CATEGORY, Channels.base(() -> ""),
                REPLY, Channels.base(() -> "")
        );

        public TriageState(Map<String, Object> initData) {
            super(initData);
        }

        public String ticket() {
            return this.<String>value(TICKET).orElse("");
        }

        public String category() {
            return this.<String>value(CATEGORY).orElse("");
        }

        public String reply() {
            return this.<String>value(REPLY).orElse("");
        }
    }
}
