package com.localflow.domain.project.dto.response;

import com.localflow.domain.workspace.dto.FileSyncResponse;

public record GitHubProjectImportResponse(
        ProjectResponse project,
        FileSyncResponse files,
        String repositoryUrl,
        String branch
) {
}
