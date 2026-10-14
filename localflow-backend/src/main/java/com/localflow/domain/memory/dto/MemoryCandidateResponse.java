package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.domain.MemoryCandidateStatus;
import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.entity.ProjectMemoryCandidate;
import java.time.Instant;
import java.util.Set;

public record MemoryCandidateResponse(
        String id, String projectId, MemoryCandidateSourceType sourceType, String sourceId,
        String sourceExcerpt, MemoryCandidateStatus status, MemoryType type, String title,
        String content, boolean globalScope, Set<String> scopeTags, Set<String> scopeFilePatterns,
        Set<String> scopeTaskTypes, String possibleDuplicateMemoryId, String possibleDuplicateTitle,
        String approvedMemoryId, Instant createdAt, Instant reviewedAt
) {
    public static MemoryCandidateResponse from(ProjectMemoryCandidate candidate, String duplicateTitle) {
        return new MemoryCandidateResponse(candidate.getId(), candidate.getProject().getId(),
                candidate.getSourceType(), candidate.getSourceId(), candidate.getSourceExcerpt(),
                candidate.getStatus(), candidate.getType(), candidate.getTitle(), candidate.getContent(),
                candidate.isGlobalScope(), candidate.getScopeTags(), candidate.getScopeFilePatterns(),
                candidate.getScopeTaskTypes(), candidate.getPossibleDuplicateMemoryId(), duplicateTitle,
                candidate.getApprovedMemoryId(), candidate.getCreatedAt(), candidate.getReviewedAt());
    }
}
