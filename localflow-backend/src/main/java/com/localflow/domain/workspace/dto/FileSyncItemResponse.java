package com.localflow.domain.workspace.dto;

import com.localflow.domain.workspace.domain.FileChangeType;

public record FileSyncItemResponse(String relativePath, FileChangeType changeType, String reason) {
}
