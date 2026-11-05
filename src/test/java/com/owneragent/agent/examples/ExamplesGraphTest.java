package com.owneragent.agent.examples;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证注册到 Studio 的两张示例图与 Builder 目录里的实现行为一致。
 */
class ExamplesGraphTest {

    @Test
    void triageRoutesNormalTicketToAutoReply() throws Exception {
        var result = triageGraph()
                .invoke(GraphInput.args(Map.of(SupportTriageGraph.TICKET, "帮我改一下收货地址")),
                        RunnableConfig.empty())
                .orElseThrow();

        assertEquals("auto", result.category());
        assertTrue(result.reply().startsWith("已自动回复"), result.reply());
    }

    @Test
    void triageRoutesSensitiveTicketToHuman() throws Exception {
        var result = triageGraph()
                .invoke(GraphInput.args(Map.of(SupportTriageGraph.TICKET, "我要投诉并申请退款")),
                        RunnableConfig.empty())
                .orElseThrow();

        assertEquals("human", result.category());
        assertTrue(result.reply().startsWith("已转人工"), result.reply());
    }

    @Test
    void draftLoopFinalizesWhenScorePasses() throws Exception {
        var result = draftGraph()
                .invoke(GraphInput.args(Map.of(
                        DraftReviewGraph.TOPIC, "LangGraph4j 入门",
                        DraftReviewGraph.MAX_ROUNDS, 5)),
                        RunnableConfig.empty())
                .orElseThrow();

        assertEquals(2, result.round());
        assertEquals(100, result.score());
        assertEquals("finalized", result.status());
        assertEquals(
                List.of("plan", "draft", "review", "revise", "draft", "review", "finalize"),
                result.trail());
        assertTrue(result.draft().endsWith("✅"), result.draft());
    }

    @Test
    void draftLoopAbortsWhenRoundBudgetIsExhausted() throws Exception {
        var result = draftGraph()
                .invoke(GraphInput.args(Map.of(
                        DraftReviewGraph.TOPIC, "预算不足的稿子",
                        DraftReviewGraph.MAX_ROUNDS, 1)),
                        RunnableConfig.empty())
                .orElseThrow();

        assertEquals(1, result.round());
        assertEquals(75, result.score());
        assertEquals("aborted", result.status());
        assertEquals(List.of("plan", "draft", "review", "abort"), result.trail());
    }

    private static CompiledGraph<SupportTriageGraph.TriageState> triageGraph() throws Exception {
        return SupportTriageGraph.stateGraph().compile(CompileConfig.builder().build());
    }

    private static CompiledGraph<DraftReviewGraph.ReviewState> draftGraph() throws Exception {
        return DraftReviewGraph.stateGraph().compile(CompileConfig.builder().build());
    }
}
