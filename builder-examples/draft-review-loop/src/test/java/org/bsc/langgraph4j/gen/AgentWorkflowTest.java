package org.bsc.langgraph4j.gen;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
* This is an automatically generated file.
*
* This file was generated using `langgraph4j-gen`.
*
* This file provides a placeholder implementation for the corresponding stub.
*
* Replace the placeholder implementation with your own logic.
*/
public class AgentWorkflowTest {

    @Test
    public void workflowTest() throws Exception {

        var graph = new AgentWorkflowBuilder<MessagesState<String>>()
            .plan( node_async(state -> {
            System.out.println( "plan" );
            return Map.of();
            }))
            .draft( node_async(state -> {
            System.out.println( "draft" );
            return Map.of();
            }))
            .review( node_async(state -> {
            System.out.println( "review" );
            return Map.of();
            }))
            .revise( node_async(state -> {
            System.out.println( "revise" );
            return Map.of();
            }))
            .finalize( node_async(state -> {
            System.out.println( "finalize" );
            return Map.of();
            }))
            .abort( node_async(state -> {
            System.out.println( "abort" );
            return Map.of();
            }))
                .route( edge_async( state -> {
                System.out.println( "route" );
                // Builder 生成的是占位符 `return ;`，必须返回 paths 中的一个。
                return "finalize";
                }))
        .build( MessagesState::new, MessagesState.SCHEMA );


        var compileConfig = CompileConfig.builder()
                            // Add persistence
                            // .checkpointSaver(new MemorySaver())
                            // add interruptions
                            // .interruptAfter( ... )  // Before nodes
                            // .interruptBefore( ) // After nodes
                            .build();

        var workflow = graph.compile( compileConfig );

        var runnableConfig = RunnableConfig.builder()
                            // add thread Id (i.e. execution session id )
                            // valid only if checkpointSaver is enabled (see above)
                            // .threadId( "my_thread" )
                            .build();

        var iterator = workflow.stream( Map.of(), runnableConfig );

        for( var out : iterator ) {
            System.out.println( out );
        }
    }
}