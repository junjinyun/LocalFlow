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
public class AgentRunDispatchService {
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentRunDispatchService(AgentRunService runService,
                                   AgentRunRepository runRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentRunResponse enqueue(String projectId, String runId, boolean approved) {
        AgentRun run = runService.requireRunForUpdate(projectId, runId);
        AgentRunStatus previousStatus = run.getStatus();
        if (previousStatus != AgentRunStatus.DRAFT
                && previousStatus != AgentRunStatus.WAITING_APPROVAL) {
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }
        if (previousStatus == AgentRunStatus.WAITING_APPROVAL && !approved) {
            return AgentRunResponse.from(run);
        }

        boolean approvalResume = previousStatus == AgentRunStatus.WAITING_APPROVAL;
        run.queue();
        runRepository.saveAndFlush(run);
        eventPublisher.publishEvent(new AgentRunQueuedEvent(
                projectId, runId, approved, approvalResume));
        return AgentRunResponse.from(run);
    }
}
