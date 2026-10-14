package com.localflow.domain.validation.domain;

import java.util.List;

public record ValidationCommandSpec(
        String id,
        ValidationBuildTool tool,
        ValidationCommandType type,
        String workingDirectory,
        String buildFile,
        String displayName,
        String displayCommand,
        String executable,
        List<String> arguments
) {
    public ValidationCommandSpec {
        arguments = List.copyOf(arguments);
    }
}
