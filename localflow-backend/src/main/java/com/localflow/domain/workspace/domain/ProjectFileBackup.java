package com.localflow.domain.workspace.domain;

import java.util.Set;

public record ProjectFileBackup(
        String relativePath,
        boolean existed,
        byte[] content,
        Set<String> automaticTags,
        Set<String> userAddedTags,
        Set<String> userExcludedTags
) {
    public static ProjectFileBackup missing(String relativePath) {
        return new ProjectFileBackup(relativePath, false, null, Set.of(), Set.of(), Set.of());
    }
}
