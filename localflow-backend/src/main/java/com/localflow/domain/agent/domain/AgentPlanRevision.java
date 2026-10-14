package com.localflow.domain.agent.domain;

import java.time.Instant;

public record AgentPlanRevision(
        int version,
        String revisionInstruction,
        String planJson,
        String changesJson,
        Instant archivedAt
) {
}
