package com.localflow.domain.memory.service;

import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import java.time.Instant;
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
    private final MemoryCandidateExtractionHistoryService historyService;

    public MemoryCandidateExtractionListener(ProjectMemoryCandidateService candidateService,
                                             MemoryCandidateExtractionHistoryService historyService) {
        this.candidateService = candidateService;
        this.historyService = historyService;
    }

    @Async("memoryCandidateTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(MemoryCandidateExtractionRequestedEvent event) {
        Instant startedAt = Instant.now();
        MemoryCandidateExtractionTrigger trigger =
                MemoryCandidateExtractionTrigger.from(event.sourceType());
        try {
            int createdCount = candidateService.extractSource(event.projectId(), event.sourceType(),
                    event.sourceId(), event.content()).size();
            historyService.recordSuccess(event.projectId(), trigger, event.sourceType(),
                    event.sourceId(), createdCount, startedAt);
        } catch (RuntimeException exception) {
            recordFailure(event, trigger, startedAt, exception);
            log.warn("기억 후보 자동 추출에 실패했습니다. projectId={}, sourceType={}, sourceId={}",
                    event.projectId(), event.sourceType(), event.sourceId(), exception);
        }
    }

    private void recordFailure(MemoryCandidateExtractionRequestedEvent event,
                               MemoryCandidateExtractionTrigger trigger,
                               Instant startedAt, RuntimeException exception) {
        try {
            historyService.recordFailure(event.projectId(), trigger, event.sourceType(),
                    event.sourceId(), exception.getMessage(), startedAt);
        } catch (RuntimeException historyException) {
            log.warn("기억 후보 추출 실패 이력을 저장하지 못했습니다. sourceId={}",
                    event.sourceId(), historyException);
        }
    }
}
