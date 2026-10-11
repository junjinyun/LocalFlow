package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentInputSnapshot(
        List<String> contextFiles,
        List<MemoryReference> memories,
        String systemPrompt,
        String userPrompt
) {
    public AgentInputSnapshot {
        contextFiles = contextFiles == null ? List.of() : List.copyOf(contextFiles);
        memories = memories == null ? List.of() : List.copyOf(memories);
    }

    public record MemoryReference(String id, String title) { }
}
