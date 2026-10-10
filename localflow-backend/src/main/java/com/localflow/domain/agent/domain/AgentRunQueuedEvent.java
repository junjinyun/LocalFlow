package com.localflow.domain.agent.domain;

public record AgentRunQueuedEvent(
        String projectId,
        String runId,
        boolean approved,
        boolean approvalResume
) {
}
