package com.localflow.domain.workspace.domain;

import java.util.Set;

public record ProjectFileBackup(
        String relativePath,
        boolean existed,
        byte[] content,
        Set<String> tags
) {
    public static ProjectFileBackup missing(String relativePath) {
        return new ProjectFileBackup(relativePath, false, null, Set.of());
    }
}
