package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentRunQueuedEvent;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunRetryService {
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentRunRetryService(AgentRunService runService,
                                AgentRunRepository runRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentRunResponse retry(String projectId, String sourceRunId) {
        AgentRun source = runService.requireRunForUpdate(projectId, sourceRunId);
        if (source.getRetriedByRunId() != null) {
            return AgentRunResponse.from(runService.requireRun(projectId, source.getRetriedByRunId()));
        }
        if (source.getStatus() != AgentRunStatus.FAILED
                || !Boolean.TRUE.equals(source.getErrorRetryable())) {
            throw new CustomException(ErrorCode.AGENT_RUN_NOT_RETRYABLE);
        }
        if (runService.hasOtherActiveRun(projectId, sourceRunId)) {
            throw new CustomException(ErrorCode.AGENT_RUN_ALREADY_ACTIVE);
        }

        AgentRun retry = AgentRun.retryOf(source);
        source.markRetriedBy(retry.getId());
        retry.queue();
        runRepository.save(source);
        runRepository.saveAndFlush(retry);
        eventPublisher.publishEvent(new AgentRunQueuedEvent(
                projectId, retry.getId(), false, false));
        return AgentRunResponse.from(retry);
    }
}
