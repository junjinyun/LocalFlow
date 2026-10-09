package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.entity.ProjectMemory;
import java.time.Instant;

public record ProjectMemoryResponse(
        String id, String projectId, MemoryType type, String title, String content,
        boolean active, Instant createdAt, Instant updatedAt
) {
    public static ProjectMemoryResponse from(ProjectMemory memory) {
        return new ProjectMemoryResponse(memory.getId(), memory.getProject().getId(),
                memory.getType(), memory.getTitle(), memory.getContent(), memory.isActive(),
                memory.getCreatedAt(), memory.getUpdatedAt());
    }
}
