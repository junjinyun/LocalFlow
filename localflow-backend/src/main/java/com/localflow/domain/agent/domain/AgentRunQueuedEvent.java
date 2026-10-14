package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentRunQueuedEvent(
        String projectId,
        String runId,
        boolean approved,
        boolean approvalResume,
        List<String> selectedOperationIds
) {
    public AgentRunQueuedEvent {
        selectedOperationIds = selectedOperationIds == null ? null : List.copyOf(selectedOperationIds);
    }
}
