package com.owneragent.agent.config;

import com.owneragent.agent.react.*;
import com.owneragent.agent.springai.DemoTools;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.spring.ai.agent.CallModelAction;
import org.bsc.langgraph4j.spring.ai.agent.ExecuteToolsAction;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.spring.ai.serializer.jackson.SpringAIJacksonStateSerializer;
import org.bsc.langgraph4j.utils.EdgeMappings;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class AgentConfig {

    static final String SYSTEM_PROMPT = """
            你是一个聪明的大脑
            回答内容需要在企业知识库，禁止自撰
            """;

    @Bean
    public CompiledGraph<SimpleState> agentGraph(ChatModel chatModel, DemoTools demoTools)
            throws GraphStateException {
        return agentStateGraph(chatModel, demoTools)
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        // 默认 true 会在图到达 END 时释放 checkpoint，多轮记忆会失效。
                        .releaseThread(false)
                        .build());
    }

    /**
     * 构建未编译的图。
     *
     * <p>应用本身用 {@link #agentGraph} 的已编译版本；LangGraph4j Studio 需要拿到未编译的
     * {@link StateGraph}，由它按会话用自己的 {@link CompileConfig} 编译。每次调用都返回新实例，
     * 避免两处共享同一个可变图。</p>
     */
    public static StateGraph<SimpleState> agentStateGraph(ChatModel chatModel, DemoTools demoTools)
            throws GraphStateException {

        // AgentExecutor.Builder 负责装配 ChatModel、system prompt 和工具，
        // 下面直接复用它的 CallModelAction / ExecuteToolsAction 两个内置节点，
        // 再插入自定义的 GreeterNode / ResponderNode：
        //
        // START → greeter → agent → action ─┬─ 有 tool_calls → agent
        //                                    └─ 没有 tool_calls（"end"）→ responder → END
        AgentExecutor.Builder builder = AgentExecutor.builder()
                // 最外层记录每次模型调用的入参/出参（此时日志里的内容就是图实际拿到的结果）
                .chatModel(new ModelCallLoggingChatModel(
                        // 归一化 tool-call 消息的 null content，避免 checkpoint 反序列化出字面量 "null"
                        new ToolCallContentNormalizingChatModel(chatModel)))
                .defaultSystem(SYSTEM_PROMPT)
                .toolsFromObject(demoTools);

        return new StateGraph<>(SimpleState.SCHEMA,
                new SpringAIJacksonStateSerializer<SimpleState>(SimpleState::new))
                .addNode("greeter", node_async(new GreeterNode()))
                .addNode("agent", new CallModelAction<SimpleState>(builder))
                .addNode("action", new ExecuteToolsAction<SimpleState>(builder.tools()),
                        // ExecuteToolsAction 用 "end" 表示工具循环结束；把该 label 映射到 responder。
                        EdgeMappings.builder()
                                .to("agent")
                                .to("responder", "end")
                                .build())
                .addNode("responder", node_async(new ResponderNode()))
                .addEdge(START, "greeter")
                .addEdge("greeter", "agent")
                .addEdge("agent", "action")
                .addEdge("responder", END);
    }
}
