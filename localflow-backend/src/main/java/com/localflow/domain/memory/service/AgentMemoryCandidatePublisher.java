package com.localflow.domain.memory.service;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AgentMemoryCandidatePublisher {
    private static final Logger log = LoggerFactory.getLogger(AgentMemoryCandidatePublisher.class);

    private final MemoryCandidateExtractionDispatchService dispatchService;

    public AgentMemoryCandidatePublisher(MemoryCandidateExtractionDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public void request(AgentRun run) {
        if (run.getStatus() != AgentRunStatus.COMPLETED
                && run.getStatus() != AgentRunStatus.PARTIALLY_COMPLETED) return;
        try {
            dispatchService.request(run.getProject().getId(), MemoryCandidateSourceType.AGENT_RUN,
                    run.getId(), run.getResultMessage());
        } catch (RuntimeException exception) {
            log.warn("완료된 실행의 기억 후보 추출 요청에 실패했습니다. runId={}",
                    run.getId(), exception);
        }
    }
}
