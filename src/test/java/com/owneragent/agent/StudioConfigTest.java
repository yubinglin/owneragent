package com.owneragent.agent;

import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import com.owneragent.agent.springai.DemoTools;
import com.owneragent.agent.react.ScriptedChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioConfigTest {

    private static StudioConfig config() throws Exception {
        return new StudioConfig(new ScriptedChatModel(), new DemoTools());
    }

    @Test
    void exposesOwnerAgentGraphWithMessageInput() throws Exception {
        var instance = config().instanceMap().get(StudioConfig.INSTANCE_ID);
        assertNotNull(instance, "Studio 应注册 owneragent 实例");
        assertEquals("messages", instance.args().get(0).name(), "输入参数名必须对应 messages 通道");

        Object converted = instance.args().get(0).converter().apply("你好");
        UserMessage message = assertInstanceOf(UserMessage.class, converted,
                "Studio 的字符串输入应转成 UserMessage");
        assertEquals("你好", message.getText());
    }

    @Test
    void registersBuilderExamplesAlongsideOwnerAgent() throws Exception {
        Map<String, LangGraphStudioServer.Instance> instances = config().instanceMap();

        assertEquals(
                Set.of(StudioConfig.INSTANCE_ID, StudioConfig.SUPPORT_TRIAGE_ID, StudioConfig.DRAFT_REVIEW_ID),
                instances.keySet());

        var triageArgs = instances.get(StudioConfig.SUPPORT_TRIAGE_ID).args();
        assertEquals(List.of("ticket"), triageArgs.stream().map(arg -> arg.name()).toList());
        assertTrue(triageArgs.get(0).required(), "ticket 应是必填参数");

        var draftArgs = instances.get(StudioConfig.DRAFT_REVIEW_ID).args();
        assertEquals(List.of("topic", "maxRounds"), draftArgs.stream().map(arg -> arg.name()).toList());
        assertTrue(draftArgs.get(0).required(), "topic 应是必填参数");
        assertFalse(draftArgs.get(1).required(), "maxRounds 应是可选参数");

        var maxRoundsConverter = draftArgs.get(1).converter();
        assertEquals(5, maxRoundsConverter.apply("5"));
        assertEquals(3, maxRoundsConverter.apply(""), "留空时应回退到默认轮数");
        assertEquals(1, maxRoundsConverter.apply("0"), "min 轮数应被夹到 1");
        assertThrows(IllegalArgumentException.class, () -> maxRoundsConverter.apply("abc"),
                "非数字应给出明确的参数错误");
    }

    @Test
    void initEndpointReturnsGraphDefinition() throws Exception {
        var servlet = new LangGraphStudioServer.GraphInitServlet(config().instanceMap());
        servlet.init(new MockServletConfig());

        assertInit(servlet, StudioConfig.INSTANCE_ID,
                "greeter", "responder", "messages");
        assertInit(servlet, StudioConfig.SUPPORT_TRIAGE_ID,
                "classify", "auto_reply", "escalate", "ticket");
        assertInit(servlet, StudioConfig.DRAFT_REVIEW_ID,
                "plan", "draft", "review", "revise", "finalize", "abort", "maxRounds");
    }

    private static void assertInit(LangGraphStudioServer.GraphInitServlet servlet,
                                   String instanceId,
                                   String... expectedFragments) throws Exception {
        var request = new MockHttpServletRequest("GET", "/init");
        request.setParameter("instance", instanceId);
        var response = new MockHttpServletResponse();
        // doGet 是 protected，通过 HttpServlet#service 走正常的 GET 分发
        servlet.service(request, response);

        String json = response.getContentAsString();
        assertTrue(json.contains("\"id\":\"" + instanceId + "\""),
                "响应应包含 instance id " + instanceId + "，实际为: " + json);
        // 图定义由 JsonDslGenerator 生成，节点名会原样出现在 JSON 字符串里
        for (String fragment : expectedFragments) {
            assertTrue(json.contains(fragment),
                    "图定义应包含 " + fragment + "，实际为: " + json);
        }
        assertTrue(response.getContentType().startsWith("application/json"));
    }
}
