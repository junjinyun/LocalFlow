package com.localflow.domain.agent.domain;

import java.time.Duration;
import java.time.Instant;
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
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Long durationMillis,
        Integer generationAttempts
) {
    public AgentTaskExecution {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public AgentTaskExecution withStatus(AgentTaskStatus next, String plan, String changes,
                                         String result, String error) {
        Instant now = Instant.now();
        Instant nextStartedAt = next == AgentTaskStatus.RUNNING && startedAt == null ? now : startedAt;
        boolean terminal = next == AgentTaskStatus.COMPLETED || next == AgentTaskStatus.FAILED
                || next == AgentTaskStatus.SKIPPED;
        Instant nextCompletedAt = terminal ? now : completedAt;
        Long nextDuration = durationMillis;
        if (terminal && nextStartedAt != null) {
            nextDuration = Duration.between(nextStartedAt, now).toMillis();
        }
        return new AgentTaskExecution(taskId, instruction, dependsOn, order, next,
                plan, changes, result, error, nextStartedAt, nextCompletedAt,
                nextDuration, generationAttempts);
    }

    public AgentTaskExecution withGenerationAttempts(int attempts) {
        return new AgentTaskExecution(taskId, instruction, dependsOn, order, status,
                planJson, changesJson, resultMessage, errorMessage, startedAt,
                completedAt, durationMillis, attempts);
    }
}
