package com.localflow.domain.agent.domain;

public record FileOperationPlan(
        String id,
        FileOperationAction action,
        String path,
        String destinationPath,
        String content
) {
    public FileOperationPlan(FileOperationAction action, String path,
                             String destinationPath, String content) {
        this(null, action, path, destinationPath, content);
    }
}
