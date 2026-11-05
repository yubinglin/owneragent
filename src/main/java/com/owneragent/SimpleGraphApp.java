package com.owneragent;

import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.GraphResult;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import com.owneragent.agent.react.GreeterNode;
import com.owneragent.agent.react.ResponderNode;
import com.owneragent.agent.react.SimpleState;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public class SimpleGraphApp {

    public static void main(String[] args) throws GraphStateException {
        // Initialize nodes
        GreeterNode greeterNode = new GreeterNode();
        ResponderNode responderNode = new ResponderNode();

        // Define the graph structure
        var stateGraph = new StateGraph<>(SimpleState.SCHEMA, initData -> new SimpleState(initData))
                .addNode("greeter", node_async(greeterNode))
                .addNode("responder", node_async(responderNode))
                // Define edges
                .addEdge(START, "greeter") // Start with the greeter node
                .addEdge("greeter", "responder")
                .addEdge("responder", END)   // End after the responder node
                ;
        // Compile the graph
        var compiledGraph = stateGraph.compile();

        // Run the graph
        Map<String, Object> inputs = Map.of("input", "Developer");
        RunnableConfig config = RunnableConfig.empty();
        //atuhor abing like mm
        var stream = compiledGraph.stream( GraphInput.args(inputs), config ); // start streaming outputs from the graph execution
        try {
            stream.forEachAsync( output -> // process each output as it is produced
                            System.out.println("Step executed: " + output))
                    .thenApply( GraphResult::from ) // holds final result in GraphResult
                    .thenAccept( result -> { // process the final result
                        if (result.isStateDataOrCheckpointSaverTag()) {
                            System.out.println("Final state: " + result.asStateDataOrLastCheckpointStateData());
                        } else if (result.isInterruptionMetadata()) {
                            System.out.println("Graph interrupted: " + result.asInterruptionMetadata());
                        }
                    })
                    .join(); // block until the graph execution completes, otherwise main() exits early
        } finally {
            // Interrupt=true because async-generator 5.1.0's close() does not shut down its
            // (non-daemon) executor; without this the JVM never exits.
            stream.cancel(true);
        }

    }
}