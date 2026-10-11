package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.entity.ProjectMemory;
import java.time.Instant;
import java.util.Set;

public record ProjectMemoryResponse(
        String id, String projectId, MemoryType type, String title, String content,
        boolean active, boolean globalScope, Set<String> scopeTags,
        Set<String> scopeFilePatterns, Set<String> scopeTaskTypes,
        Instant createdAt, Instant updatedAt
) {
    public static ProjectMemoryResponse from(ProjectMemory memory) {
        return new ProjectMemoryResponse(memory.getId(), memory.getProject().getId(),
                memory.getType(), memory.getTitle(), memory.getContent(), memory.isActive(),
                memory.isGlobalScope(), memory.getScopeTags(), memory.getScopeFilePatterns(),
                memory.getScopeTaskTypes(),
                memory.getCreatedAt(), memory.getUpdatedAt());
    }
}
