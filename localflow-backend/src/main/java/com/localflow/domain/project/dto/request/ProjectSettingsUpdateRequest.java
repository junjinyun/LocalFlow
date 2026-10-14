package com.localflow.domain.project.dto.request;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.PrivacyMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ProjectSettingsUpdateRequest(
        @NotNull ExecutionMode executionMode,
        @NotNull PrivacyMode privacyMode,
        @NotNull PermissionPolicy readPolicy,
        @NotNull PermissionPolicy createPolicy,
        @NotNull PermissionPolicy editPolicy,
        @NotNull PermissionPolicy movePolicy,
        @NotNull PermissionPolicy deletePolicy,
        @NotNull PermissionPolicy executePolicy,
        @Size(max = 100) Set<@Size(max = 200) String> excludedPathPatterns,
        @Size(max = 50) Set<@Size(max = 20) String> excludedExtensions
) {
}
