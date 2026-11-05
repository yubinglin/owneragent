package org.bsc.langgraph4j.gen;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.serializer.StateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.utils.EdgeMappings;

import java.util.Map;
import java.util.Objects;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;

/**
 * This is an automatically generated file. Do not modify it.
 *
 * This file was generated using `langgraph4j-gen`.
 */
public class AgentWorkflowBuilder<State extends AgentState> {

    private AsyncNodeAction<State> plan;
    private AsyncNodeAction<State> draft;
    private AsyncNodeAction<State> review;
    private AsyncNodeAction<State> revise;
    private AsyncNodeAction<State> finalize;
    private AsyncNodeAction<State> abort;
    private AsyncEdgeAction<State> route;

        public AgentWorkflowBuilder<State> plan(AsyncNodeAction<State> plan) {
        this.plan = plan;
        return this;
        }
        public AgentWorkflowBuilder<State> draft(AsyncNodeAction<State> draft) {
        this.draft = draft;
        return this;
        }
        public AgentWorkflowBuilder<State> review(AsyncNodeAction<State> review) {
        this.review = review;
        return this;
        }
        public AgentWorkflowBuilder<State> revise(AsyncNodeAction<State> revise) {
        this.revise = revise;
        return this;
        }
        public AgentWorkflowBuilder<State> finalize(AsyncNodeAction<State> finalize) {
        this.finalize = finalize;
        return this;
        }
        public AgentWorkflowBuilder<State> abort(AsyncNodeAction<State> abort) {
        this.abort = abort;
        return this;
        }

            public AgentWorkflowBuilder<State> route(AsyncEdgeAction<State> route) {
            this.route = route;
            return this;
            }

    private StateGraph<State> of(AgentStateFactory<State> factory) {
        return new StateGraph<>( factory );
    }

    private StateGraph<State> of( StateSerializer<State> serializer) {
        return new StateGraph<>( serializer );
    }

    private StateGraph<State> of(Map<String, Channel<?>> channels, AgentStateFactory<State> factory) {
        // 上游 1.6-SNAPSHOT 生成器漏传了 channels，这里补上。
        return new StateGraph<>(channels, factory);
    }

    private StateGraph<State> of(Map<String, Channel<?>> channels, StateSerializer<State> serializer) {
        // 同上：保留调用方传入的 channels。
        return new StateGraph<>(channels, serializer);
    }

    private StateGraph<State> build( StateGraph<State> graph ) throws GraphStateException {
            Objects.requireNonNull( plan, "plan cannot be null");
            Objects.requireNonNull( draft, "draft cannot be null");
            Objects.requireNonNull( review, "review cannot be null");
            Objects.requireNonNull( revise, "revise cannot be null");
            Objects.requireNonNull( finalize, "finalize cannot be null");
            Objects.requireNonNull( abort, "abort cannot be null");
                Objects.requireNonNull( route, "route cannot be null");

        return graph
            .addNode( "plan", plan )
            .addNode( "draft", draft )
            .addNode( "review", review )
            .addNode( "revise", revise )
            .addNode( "finalize", finalize )
            .addNode( "abort", abort )
                .addEdge( START, "plan" )
                .addEdge( "plan", "draft" )
                .addEdge( "draft", "review" )
                .addConditionalEdges( "review",
                route,
                EdgeMappings.builder()
                    .to( "revise" )
                    .to( "finalize" )
                    .to( "abort" )
                .build())
                .addEdge( "revise", "draft" )
                .addEdge( "finalize", END )
                .addEdge( "abort", END )
        ;

    }

    public StateGraph<State> build( AgentStateFactory<State> factory ) throws GraphStateException {
        return build( of( factory ) );
    }

    public StateGraph<State> build( StateSerializer<State> serializer ) throws GraphStateException {
        return build( of( serializer ) );
    }

    public StateGraph<State> build(AgentStateFactory<State> factory, Map<String, Channel<?>> channels ) throws GraphStateException {
        return build(of( channels, factory ));
    }

    public StateGraph<State> build(Map<String, Channel<?>> channels, StateSerializer<State> serializer) throws GraphStateException {
        return build(of( channels, serializer ));
    }

}