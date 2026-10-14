package com.localflow.domain.workspace.dto;

import java.util.List;

public record FileSyncResponse(
        int added, int modified, int unchanged, int deleted, int ignored,
        long totalFiles, boolean scopedSync, List<String> scopePaths,
        List<FileSyncItemResponse> changes
) {
    public FileSyncResponse {
        scopePaths = scopePaths == null ? List.of() : List.copyOf(scopePaths);
    }
}
