package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentInputSnapshot(
        List<String> contextFiles,
        String systemPrompt,
        String userPrompt
) {
    public AgentInputSnapshot {
        contextFiles = contextFiles == null ? List.of() : List.copyOf(contextFiles);
    }
}
