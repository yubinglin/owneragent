package com.owneragent.agent.web;

import java.util.List;

public record ChatReply(String threadId, String reply, List<AgentStep> steps) {
}
