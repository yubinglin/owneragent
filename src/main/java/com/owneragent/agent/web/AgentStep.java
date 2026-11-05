package com.owneragent.agent.web;

/**
 * 一次工具调用的轨迹，便于前端展示 Agent 的执行过程。
 *
 * @param type   tool_call 或 tool_result
 * @param name   工具名
 * @param detail 调用参数或返回内容
 */
public record AgentStep(String type, String name, String detail) {
}
