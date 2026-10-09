package com.localflow.domain.agent.domain;

public record AgentFileChangeSnapshot(
        FileOperationAction action,
        String path,
        String destinationPath,
        String beforeContent,
        String afterContent
) {
}
