package com.localflow.domain.agent.dto.request;

import java.util.List;

public record AgentRunExecuteRequest(Boolean approved, List<String> selectedOperationIds) {
    public AgentRunExecuteRequest {
        selectedOperationIds = selectedOperationIds == null ? null : List.copyOf(selectedOperationIds);
    }

    public boolean isApproved() {
        return Boolean.TRUE.equals(approved);
    }
}
