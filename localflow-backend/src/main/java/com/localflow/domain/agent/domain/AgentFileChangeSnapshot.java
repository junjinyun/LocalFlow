package com.localflow.domain.agent.domain;

public record AgentFileChangeSnapshot(
        String operationId,
        FileOperationAction action,
        String path,
        String destinationPath,
        String beforeContent,
        String afterContent
) {
    public AgentFileChangeSnapshot(FileOperationAction action, String path, String destinationPath,
                                   String beforeContent, String afterContent) {
        this(null, action, path, destinationPath, beforeContent, afterContent);
    }
}
