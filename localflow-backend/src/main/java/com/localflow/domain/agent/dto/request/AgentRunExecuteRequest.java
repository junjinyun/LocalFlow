package com.localflow.domain.agent.dto.request;

public record AgentRunExecuteRequest(Boolean approved) {
    public boolean isApproved() {
        return Boolean.TRUE.equals(approved);
    }
}
