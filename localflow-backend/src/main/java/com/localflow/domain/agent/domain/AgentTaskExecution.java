package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentTaskExecution(
        String taskId,
        String instruction,
        List<String> dependsOn,
        int order,
        AgentTaskStatus status,
        String planJson,
        String changesJson,
        String resultMessage,
        String errorMessage
) {
    public AgentTaskExecution {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public AgentTaskExecution withStatus(AgentTaskStatus next, String plan, String changes,
                                         String result, String error) {
        return new AgentTaskExecution(taskId, instruction, dependsOn, order, next,
                plan, changes, result, error);
    }
}
