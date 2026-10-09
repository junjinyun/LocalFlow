package com.localflow.domain.workspace.dto;

import java.util.List;

public record FileSyncResponse(
        int added, int modified, int unchanged, int deleted, int ignored,
        long totalFiles, List<FileSyncItemResponse> changes
) {
}
