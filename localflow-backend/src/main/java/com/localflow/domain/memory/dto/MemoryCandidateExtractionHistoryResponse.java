package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionStatus;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.entity.MemoryCandidateExtractionHistory;
import java.time.Instant;

public record MemoryCandidateExtractionHistoryResponse(
        String id,
        MemoryCandidateExtractionTrigger triggerType,
        MemoryCandidateSourceType sourceType,
        String sourceId,
        MemoryCandidateExtractionStatus status,
        int createdCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public static MemoryCandidateExtractionHistoryResponse from(
            MemoryCandidateExtractionHistory history) {
        return new MemoryCandidateExtractionHistoryResponse(
                history.getId(), history.getTriggerType(), history.getSourceType(),
                history.getSourceId(), history.getStatus(), history.getCreatedCount(),
                history.getErrorMessage(), history.getStartedAt(), history.getCompletedAt());
    }
}
