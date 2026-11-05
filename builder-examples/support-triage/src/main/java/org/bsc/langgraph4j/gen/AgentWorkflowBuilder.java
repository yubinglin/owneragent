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

    private AsyncNodeAction<State> classify;
    private AsyncNodeAction<State> auto_reply;
    private AsyncNodeAction<State> escalate;
    private AsyncEdgeAction<State> route;

        public AgentWorkflowBuilder<State> classify(AsyncNodeAction<State> classify) {
        this.classify = classify;
        return this;
        }
        public AgentWorkflowBuilder<State> auto_reply(AsyncNodeAction<State> auto_reply) {
        this.auto_reply = auto_reply;
        return this;
        }
        public AgentWorkflowBuilder<State> escalate(AsyncNodeAction<State> escalate) {
        this.escalate = escalate;
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
            Objects.requireNonNull( classify, "classify cannot be null");
            Objects.requireNonNull( auto_reply, "auto_reply cannot be null");
            Objects.requireNonNull( escalate, "escalate cannot be null");
                Objects.requireNonNull( route, "route cannot be null");

        return graph
            .addNode( "classify", classify )
            .addNode( "auto_reply", auto_reply )
            .addNode( "escalate", escalate )
                .addEdge( START, "classify" )
                .addConditionalEdges( "classify",
                route,
                EdgeMappings.builder()
                    .to( "auto_reply" )
                    .to( "escalate" )
                .build())
                .addEdge( "auto_reply", END )
                .addEdge( "escalate", END )
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