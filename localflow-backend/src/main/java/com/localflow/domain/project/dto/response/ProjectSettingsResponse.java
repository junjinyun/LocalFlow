package com.localflow.domain.project.dto.response;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.PrivacyMode;
import com.localflow.domain.project.entity.ProjectSettings;
import java.time.Instant;
import java.util.Set;

public record ProjectSettingsResponse(
        String projectId, ExecutionMode executionMode, PrivacyMode privacyMode,
        PermissionPolicy readPolicy, PermissionPolicy createPolicy,
        PermissionPolicy editPolicy, PermissionPolicy movePolicy,
        PermissionPolicy deletePolicy, PermissionPolicy executePolicy,
        Set<String> excludedPathPatterns, Set<String> excludedExtensions,
        Instant updatedAt
) {
    public static ProjectSettingsResponse from(ProjectSettings settings) {
        return new ProjectSettingsResponse(
                settings.getProjectId(), settings.getExecutionMode(), settings.getPrivacyMode(),
                settings.getReadPolicy(), settings.getCreatePolicy(), settings.getEditPolicy(),
                settings.getMovePolicy(), settings.getDeletePolicy(), settings.getExecutePolicy(),
                settings.getExcludedPathPatterns(), settings.getExcludedExtensions(),
                settings.getUpdatedAt());
    }
}
