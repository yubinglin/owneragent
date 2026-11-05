package com.owneragent.agent.springai;

import com.owneragent.agent.react.Calculator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 演示用工具集。Spring AI 通过 {@code ToolCallbacks.from(object)} 自动扫描
 * {@link Tool} 注解方法，并在模型发起 tool_calls 时回调。
 */
@Component
public class DemoTools {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.SIMPLIFIED_CHINESE);

    private static final Map<String, String> KNOWLEDGE_BASE = new LinkedHashMap<>();

    static {
        KNOWLEDGE_BASE.put("owneragent 项目",
                "OwnerAgent 是一个基于 Spring AI + LangGraph4j + DeepSeek 的演示 Agent，"
                        + "使用 ReAct 图在模型与工具之间循环，并用 MemorySaver 按 threadId 保存多轮会话。");
        KNOWLEDGE_BASE.put("langgraph4j",
                "LangGraph4j 是 LangGraph 的 Java 移植，用 StateGraph 描述节点和边；"
                        + "langgraph4j-springai-agentexecutor 提供了现成的 agent -> action ReAct 图。");
        KNOWLEDGE_BASE.put("deepseek 模型",
                "默认使用 deepseek-chat，可在 application.yml 的 spring.ai.deepseek.chat.model 中切换；"
                        + "DeepSeek 提供 OpenAI 兼容接口，Spring AI 有官方 DeepSeek 模块。");
        KNOWLEDGE_BASE.put("工具调用",
                "模型返回 tool_calls 后，ExecuteToolsAction 会执行对应工具，"
                        + "把 ToolResponseMessage 追加到 messages，再回到 agent 节点继续推理。");
        KNOWLEDGE_BASE.put("工作时间",
                "团队工作时间为周一至周五 09:30-18:30（Asia/Shanghai），午休 12:00-13:30。");
    }

    @Tool(description = "获取指定 IANA 时区的当前日期和时间，例如 Asia/Shanghai")
    public String getCurrentDateTime(
            @ToolParam(description = "IANA 时区 ID，例如 Asia/Shanghai；留空则使用系统默认时区") String zoneId) {
        try {
            ZoneId zone = (zoneId == null || zoneId.isBlank())
                    ? ZoneId.systemDefault()
                    : ZoneId.of(zoneId.trim());
            return ZonedDateTime.now(zone).format(DATE_TIME_FORMAT)+"---test---";
        } catch (DateTimeException e) {
            return "无效的时区: " + zoneId;
        }
    }

    @Tool(description = "计算四则运算表达式，支持 + - * / % 和括号，例如 (12+8)*3")
    public String calculate(
            @ToolParam(description = "要计算的表达式，只能包含数字、小数点、空格和 + - * / % ( )") String expression) {
        try {
            double result = Calculator.evaluate(expression);
            return expression + " = " + Calculator.format(result);
        } catch (IllegalArgumentException e) {
            return "计算失败：" + e.getMessage();
        }
    }

    @Tool(description = "在团队内部知识库中检索资料，返回最相关的条目")
    public String searchKnowledgeBase(
            @ToolParam(description = "要检索的关键词或问题，例如 项目介绍、工作时间") String query) {
        if (query == null || query.isBlank()) {
            return "检索关键词不能为空";
        }
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        List<String> matches = KNOWLEDGE_BASE.entrySet().stream()
                .filter(entry -> entry.getKey().toLowerCase(Locale.ROOT).contains(normalized)
                        || normalized.contains(entry.getKey().toLowerCase(Locale.ROOT))
                        || entry.getValue().toLowerCase(Locale.ROOT).contains(normalized))
                .map(entry -> entry.getKey() + "：" + entry.getValue())
                .toList();
        if (matches.isEmpty()) {
            return "知识库中没有找到与「" + query + "」相关的内容。";
        }
        return String.join("\n", matches);
    }
}
