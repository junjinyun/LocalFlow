package com.localflow.domain.validation.dto;

import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.domain.ValidationCommandType;
import java.util.List;

public record ProjectValidationPreviewResponse(
        String projectId,
        List<ValidationCommandPreview> commands,
        List<String> notices
) {
    public ProjectValidationPreviewResponse {
        commands = List.copyOf(commands);
        notices = List.copyOf(notices);
    }

    public static ProjectValidationPreviewResponse from(String projectId,
                                                        List<ValidationCommandSpec> specs,
                                                        List<String> notices) {
        return new ProjectValidationPreviewResponse(projectId,
                specs.stream().map(ValidationCommandPreview::from).toList(), notices);
    }

    public record ValidationCommandPreview(
            String id,
            ValidationBuildTool tool,
            ValidationCommandType type,
            String workingDirectory,
            String buildFile,
            String displayName,
            String displayCommand,
            boolean requiresApproval
    ) {
        static ValidationCommandPreview from(ValidationCommandSpec spec) {
            return new ValidationCommandPreview(spec.id(), spec.tool(), spec.type(),
                    spec.workingDirectory(), spec.buildFile(), spec.displayName(),
                    spec.displayCommand(), true);
        }
    }
}
