package com.localflow.domain.workspace.dto;

import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.time.Instant;
import java.util.Set;

public record ProjectFileResponse(
        String id, String relativePath, String fileName, String extension,
        FileCategory category, String language, long sizeBytes,
        String sha256, Set<String> tags, Set<String> automaticTags,
        Set<String> userAddedTags, Set<String> userExcludedTags, Instant updatedAt
) {
    public static ProjectFileResponse from(ProjectFile file) {
        return new ProjectFileResponse(file.getId(), file.getRelativePath(), file.getFileName(),
                file.getExtension(), file.getCategory(), file.getLanguage(), file.getSizeBytes(),
                file.getSha256(), file.getTags(), file.getAutomaticTags(),
                file.getUserAddedTags(), file.getUserExcludedTags(), file.getUpdatedAt());
    }
}
