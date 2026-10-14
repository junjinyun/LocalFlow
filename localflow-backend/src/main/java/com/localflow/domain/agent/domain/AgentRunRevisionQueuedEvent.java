package com.localflow.domain.agent.domain;

public record AgentRunRevisionQueuedEvent(String projectId, String runId, String instruction) {
}
