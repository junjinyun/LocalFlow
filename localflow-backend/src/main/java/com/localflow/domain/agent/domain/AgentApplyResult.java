package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentApplyResult(
        boolean success,
        int appliedOperations,
        boolean rollbackAttempted,
        boolean rollbackSuccessful,
        List<String> recoveryRequiredPaths,
        String errorMessage
) {
    public AgentApplyResult {
        recoveryRequiredPaths = recoveryRequiredPaths == null
                ? List.of() : List.copyOf(recoveryRequiredPaths);
    }
}
