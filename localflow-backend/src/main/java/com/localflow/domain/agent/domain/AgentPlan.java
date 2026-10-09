package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentPlan(String summary, String response, List<FileOperationPlan> operations) {
    public AgentPlan {
        operations = operations == null ? List.of() : List.copyOf(operations);
    }
}
