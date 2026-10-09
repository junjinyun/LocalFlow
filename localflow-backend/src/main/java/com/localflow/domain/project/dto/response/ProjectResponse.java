package com.localflow.domain.project.dto.response;

import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.entity.ProjectStatus;
import java.time.Instant;

public record ProjectResponse(
        String id,
        String name,
        String description,
        String rootDirectoryName,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(),
                project.getRootDirectoryName(), project.getStatus(),
                project.getCreatedAt(), project.getUpdatedAt()
        );
    }
}
