package com.owneragent.agent.examples;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Builder 示例「带循环的草稿评审」的可运行版本，不依赖 Spring AI，可直接注册到 Studio。
 *
 * <pre>
 * START → plan → draft → review ──route──┬─ revise → draft（回边，形成循环）
 *                                         ├─ finalize → END
 *                                         └─ abort    → END
 * </pre>
 *
 * <p>每轮 {@code draft} 提升评分，达到 90 分后 finalize；
 * 达到 {@code maxRounds} 仍未达标则 abort，防止死循环。</p>
 */
public final class DraftReviewGraph {

    public static final String TOPIC = "topic";
    public static final String ROUND = "round";
    public static final String MAX_ROUNDS = "maxRounds";
    public static final String SCORE = "score";
    public static final String DRAFT = "draft";
    public static final String STATUS = "status";
    public static final String TRAIL = "trail";

    public static final int DEFAULT_MAX_ROUNDS = 3;
    public static final int PASS_SCORE = 90;

    private DraftReviewGraph() {
    }

    public static StateGraph<ReviewState> stateGraph() throws GraphStateException {
        return new StateGraph<>(ReviewState.SCHEMA, ReviewState::new)
                .addNode("plan", node_async(state -> Map.of(
                        DRAFT, "《" + state.topic() + "》初稿",
                        STATUS, "planned",
                        TRAIL, List.of("plan"))))
                .addNode("draft", node_async(state -> {
                    int nextRound = state.round() + 1;
                    int score = Math.min(100, 50 + 25 * nextRound);
                    return Map.of(
                            ROUND, nextRound,
                            SCORE, score,
                            DRAFT, "第" + nextRound + "稿：《" + state.topic() + "》",
                            STATUS, "drafted",
                            TRAIL, List.of("draft"));
                }))
                .addNode("review", node_async(state -> Map.of(
                        STATUS, "reviewed",
                        TRAIL, List.of("review"))))
                .addNode("revise", node_async(state -> Map.of(
                        STATUS, "revised",
                        TRAIL, List.of("revise"))))
                .addNode("finalize", node_async(state -> Map.of(
                        DRAFT, state.draft() + " ✅",
                        STATUS, "finalized",
                        TRAIL, List.of("finalize"))))
                .addNode("abort", node_async(state -> Map.of(
                        STATUS, "aborted",
                        TRAIL, List.of("abort"))))
                .addEdge(START, "plan")
                .addEdge("plan", "draft")
                .addEdge("draft", "review")
                .addConditionalEdges("review",
                        edge_async(state -> {
                            if (state.score() >= PASS_SCORE) {
                                return "finalize";
                            }
                            if (state.round() >= state.maxRounds()) {
                                return "abort";
                            }
                            return "revise";
                        }),
                        EdgeMappings.builder()
                                .to("finalize")
                                .to("abort")
                                .to("revise")
                                .build())
                .addEdge("revise", "draft")
                .addEdge("finalize", END)
                .addEdge("abort", END);
    }

    /**
     * 草稿评审状态：输入为 {@code topic}（必填）和 {@code maxRounds}（可选，默认 3）。
     */
    public static final class ReviewState extends AgentState {

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TOPIC, Channels.base(() -> ""),
                ROUND, Channels.base(() -> 0),
                MAX_ROUNDS, Channels.base(() -> DEFAULT_MAX_ROUNDS),
                SCORE, Channels.base(() -> 0),
                DRAFT, Channels.base(() -> ""),
                STATUS, Channels.base(() -> ""),
                // 循环中同一节点会执行多次，必须允许重复，否则第二轮 draft/review 会被去重丢弃。
                TRAIL, Channels.appenderWithDuplicate(ArrayList::new)
        );

        public ReviewState(Map<String, Object> initData) {
            super(initData);
        }

        public String topic() {
            return this.<String>value(TOPIC).orElse("");
        }

        public int round() {
            return this.<Integer>value(ROUND).orElse(0);
        }

        public int maxRounds() {
            return this.<Integer>value(MAX_ROUNDS).orElse(DEFAULT_MAX_ROUNDS);
        }

        public int score() {
            return this.<Integer>value(SCORE).orElse(0);
        }

        public String draft() {
            return this.<String>value(DRAFT).orElse("");
        }

        public String status() {
            return this.<String>value(STATUS).orElse("");
        }

        public List<String> trail() {
            return this.<List<String>>value(TRAIL).orElse(List.of());
        }
    }
}
