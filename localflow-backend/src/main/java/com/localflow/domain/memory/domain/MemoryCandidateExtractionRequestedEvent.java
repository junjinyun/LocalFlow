package com.localflow.domain.memory.domain;

public record MemoryCandidateExtractionRequestedEvent(
        String projectId,
        MemoryCandidateSourceType sourceType,
        String sourceId,
        String content
) {
}
