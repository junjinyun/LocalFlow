package com.localflow.domain.workspace.dto;

import com.localflow.domain.workspace.domain.FileAnalysisStatus;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record FileAnalysisResponse(
        ProjectFileResponse file,
        FileAnalysisStatus status,
        String role,
        String summary,
        String message,
        Set<String> automaticTags,
        Set<String> effectiveTags,
        Set<String> userAddedTags,
        Set<String> userExcludedTags,
        List<CodeSymbolResponse> symbols,
        Instant analyzedAt
) {
    public static FileAnalysisResponse from(ProjectFile file, List<CodeSymbolResponse> symbols) {
        return new FileAnalysisResponse(ProjectFileResponse.from(file), file.getAnalysisStatus(),
                file.getAnalysisRole(), file.getAnalysisSummary(), file.getAnalysisMessage(),
                file.getAutomaticTags(), file.getTags(), file.getUserAddedTags(),
                file.getUserExcludedTags(), symbols, file.getAnalyzedAt());
    }
}
