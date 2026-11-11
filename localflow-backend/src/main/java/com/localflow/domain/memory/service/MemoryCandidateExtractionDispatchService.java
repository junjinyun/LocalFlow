package com.localflow.domain.memory.service;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryCandidateExtractionDispatchService {
    private final ApplicationEventPublisher eventPublisher;

    public MemoryCandidateExtractionDispatchService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void request(String projectId, MemoryCandidateSourceType sourceType,
                        String sourceId, String content) {
        if (content == null || content.isBlank()) return;
        eventPublisher.publishEvent(new MemoryCandidateExtractionRequestedEvent(
                projectId, sourceType, sourceId, content));
    }
}
