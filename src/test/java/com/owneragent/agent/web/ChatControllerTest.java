package com.owneragent.agent.web;

import org.bsc.langgraph4j.CompiledGraph;
import com.owneragent.agent.react.SimpleState;
import com.owneragent.agent.config.AgentConfig;
import com.owneragent.agent.react.AgentService;
import com.owneragent.agent.springai.DemoTools;
import com.owneragent.agent.react.ScriptedChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc(ScriptedChatModel model) throws Exception {
        CompiledGraph<SimpleState> graph =
                new AgentConfig().agentGraph(model, new DemoTools());
        ChatController controller = new ChatController(new AgentService(graph));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsReplyAndGeneratedThreadId() throws Exception {
        MockMvc mvc = mockMvc(new ScriptedChatModel().thenReply("你好，我是 OwnerAgent。"));

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"你好\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").isNotEmpty())
                .andExpect(jsonPath("$.reply").value("你好，我是 OwnerAgent。"))
                .andExpect(jsonPath("$.steps").isArray());
    }

    @Test
    void returnsToolTrace() throws Exception {
        ScriptedChatModel model = new ScriptedChatModel()
                .thenToolCall("call-1", "calculate", "{\"expression\":\"1+1\"}")
                .thenReply("等于 2。");
        MockMvc mvc = mockMvc(model);

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threadId\":\"web-tool\",\"message\":\"算一下 1+1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("web-tool"))
                .andExpect(jsonPath("$.steps[0].type").value("tool_call"))
                .andExpect(jsonPath("$.steps[0].name").value("calculate"))
                .andExpect(jsonPath("$.steps[1].type").value("tool_result"));
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        MockMvc mvc = mockMvc(new ScriptedChatModel());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threadId\":\"t1\",\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("message 不能为空"));
    }

    @Test
    void rejectsInvalidThreadId() throws Exception {
        MockMvc mvc = mockMvc(new ScriptedChatModel());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"threadId\":\"bad id!\",\"message\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "threadId 只能包含字母、数字、下划线或短横线，长度 1-64"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        MockMvc mvc = mockMvc(new ScriptedChatModel());

        mvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("请求格式不正确"));
    }
}
