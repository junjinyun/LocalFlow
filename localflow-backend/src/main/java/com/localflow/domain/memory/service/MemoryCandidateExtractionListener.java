package com.localflow.domain.memory.service;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class MemoryCandidateExtractionListener {
    private static final Logger log = LoggerFactory.getLogger(MemoryCandidateExtractionListener.class);

    private final ProjectMemoryCandidateService candidateService;

    public MemoryCandidateExtractionListener(ProjectMemoryCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @Async("memoryCandidateTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemoryCandidateExtractionRequestedEvent event) {
        try {
            candidateService.extractSource(event.projectId(), event.sourceType(),
                    event.sourceId(), event.content());
        } catch (RuntimeException exception) {
            log.warn("기억 후보 자동 추출에 실패했습니다. projectId={}, sourceType={}, sourceId={}",
                    event.projectId(), event.sourceType(), event.sourceId(), exception);
        }
    }
}
