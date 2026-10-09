package com.localflow.domain.agent.domain;

public record FileOperationPlan(
        FileOperationAction action,
        String path,
        String destinationPath,
        String content
) {
}
