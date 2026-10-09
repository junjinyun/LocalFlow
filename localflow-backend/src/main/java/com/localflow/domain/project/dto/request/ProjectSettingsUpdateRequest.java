package com.localflow.domain.project.dto.request;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.PrivacyMode;
import jakarta.validation.constraints.NotNull;

public record ProjectSettingsUpdateRequest(
        @NotNull ExecutionMode executionMode,
        @NotNull PrivacyMode privacyMode,
        @NotNull PermissionPolicy readPolicy,
        @NotNull PermissionPolicy createPolicy,
        @NotNull PermissionPolicy editPolicy,
        @NotNull PermissionPolicy movePolicy,
        @NotNull PermissionPolicy deletePolicy,
        @NotNull PermissionPolicy executePolicy
) {
}
