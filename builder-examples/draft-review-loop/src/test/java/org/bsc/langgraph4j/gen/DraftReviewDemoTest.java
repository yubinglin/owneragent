package org.bsc.langgraph4j.gen;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 Builder 生成的 {@link AgentWorkflowBuilder} 实现一个带循环的“草稿评审”流程。
 *
 * <p>执行路径：</p>
 * <pre>
 * plan → draft → review ──route──┬─ revise → draft（循环）
 *                                 ├─ finalize → END
 *                                 └─ abort → END
 * </pre>
 *
 * <p>每轮 {@code draft} 提升评分，评分达到 90 时 finalize；超过 maxRounds 仍未达标则 abort。
 * {@code trail} 通道用 appender reducer 累积节点执行顺序，用来验证循环确实发生了。</p>
 */
class DraftReviewDemoTest {

    static class ReviewState extends AgentState {

        static final String TOPIC = "topic";
        static final String ROUND = "round";
        static final String MAX_ROUNDS = "maxRounds";
        static final String SCORE = "score";
        static final String DRAFT = "draft";
        static final String STATUS = "status";
        static final String TRAIL = "trail";

        static final Map<String, Channel<?>> SCHEMA = Map.of(
                TOPIC, Channels.base(() -> ""),
                ROUND, Channels.base(() -> 0),
                MAX_ROUNDS, Channels.base(() -> 3),
                SCORE, Channels.base(() -> 0),
                DRAFT, Channels.base(() -> ""),
                STATUS, Channels.base(() -> ""),
                // 循环中同一个节点会执行多次，必须允许重复，否则第二轮 draft/review 会被去重丢弃。
                TRAIL, Channels.appenderWithDuplicate(ArrayList::new)
        );

        ReviewState(Map<String, Object> initData) {
            super(initData);
        }

        String topic() {
            return this.<String>value(TOPIC).orElse("");
        }

        int round() {
            return this.<Integer>value(ROUND).orElse(0);
        }

        int maxRounds() {
            return this.<Integer>value(MAX_ROUNDS).orElse(3);
        }

        int score() {
            return this.<Integer>value(SCORE).orElse(0);
        }

        String draft() {
            return this.<String>value(DRAFT).orElse("");
        }

        String status() {
            return this.<String>value(STATUS).orElse("");
        }

        List<String> trail() {
            return this.<List<String>>value(TRAIL).orElse(List.of());
        }
    }

    private CompiledGraph<ReviewState> reviewGraph() throws Exception {
        var stateGraph = new AgentWorkflowBuilder<ReviewState>()
                .plan(node_async(state -> Map.of(
                        ReviewState.DRAFT, "《" + state.topic() + "》初稿",
                        ReviewState.STATUS, "planned",
                        ReviewState.TRAIL, List.of("plan"))))
                .draft(node_async(state -> {
                    var nextRound = state.round() + 1;
                    var score = Math.min(100, 50 + 25 * nextRound);
                    return Map.of(
                            ReviewState.ROUND, nextRound,
                            ReviewState.SCORE, score,
                            ReviewState.DRAFT, "第" + nextRound + "稿：《" + state.topic() + "》",
                            ReviewState.STATUS, "drafted",
                            ReviewState.TRAIL, List.of("draft"));
                }))
                .review(node_async(state -> Map.of(
                        ReviewState.STATUS, "reviewed",
                        ReviewState.TRAIL, List.of("review"))))
                .revise(node_async(state -> Map.of(
                        ReviewState.STATUS, "revised",
                        ReviewState.TRAIL, List.of("revise"))))
                .finalize(node_async(state -> Map.of(
                        ReviewState.DRAFT, state.draft() + " ✅",
                        ReviewState.STATUS, "finalized",
                        ReviewState.TRAIL, List.of("finalize"))))
                .abort(node_async(state -> Map.of(
                        ReviewState.STATUS, "aborted",
                        ReviewState.TRAIL, List.of("abort"))))
                .route(edge_async(state -> {
                    if (state.score() >= 90) {
                        return "finalize";
                    }
                    if (state.round() >= state.maxRounds()) {
                        return "abort";
                    }
                    return "revise";
                }))
                .build(ReviewState::new, ReviewState.SCHEMA);

        return stateGraph.compile(CompileConfig.builder().build());
    }

    @Test
    void loops_until_score_is_good_enough() throws Exception {
        var result = reviewGraph()
                .invoke(GraphInput.args(Map.of(
                        ReviewState.TOPIC, "LangGraph4j 入门",
                        ReviewState.MAX_ROUNDS, 5)), RunnableConfig.empty())
                .orElseThrow();

        assertEquals(2, result.round());
        assertEquals(100, result.score());
        assertEquals("finalized", result.status());
        assertEquals(
                List.of("plan", "draft", "review", "revise", "draft", "review", "finalize"),
                result.trail());
        assertTrue(result.draft().endsWith("✅"), result.draft());
        System.out.println("循环达标 -> rounds=" + result.round()
                + ", trail=" + result.trail()
                + ", draft=" + result.draft());
    }

    @Test
    void aborts_when_round_budget_is_exhausted() throws Exception {
        var result = reviewGraph()
                .invoke(GraphInput.args(Map.of(
                        ReviewState.TOPIC, "预算不足的稿子",
                        ReviewState.MAX_ROUNDS, 1)), RunnableConfig.empty())
                .orElseThrow();

        assertEquals(1, result.round());
        assertEquals(75, result.score());
        assertEquals("aborted", result.status());
        assertEquals(List.of("plan", "draft", "review", "abort"), result.trail());
        System.out.println("预算耗尽 -> rounds=" + result.round()
                + ", trail=" + result.trail()
                + ", status=" + result.status());
    }
}
