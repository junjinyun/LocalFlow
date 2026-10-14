package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentPlanRevision;
import com.localflow.domain.agent.domain.AgentRunRevisionQueuedEvent;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunRevisionService {
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AgentRunRevisionService(AgentRunService runService, AgentRunRepository runRepository,
                                   ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.runService = runService;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AgentRunResponse request(String projectId, String runId, String instruction) {
        AgentRun run = runService.requireRunForUpdate(projectId, runId);
        if (run.getStatus() != AgentRunStatus.WAITING_APPROVAL || run.getPlanJson() == null) {
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }
        List<AgentPlanRevision> revisions = revisions(run);
        revisions.add(new AgentPlanRevision(revisions.size() + 1, instruction.strip(),
                run.getPlanJson(), run.getChangesJson(), Instant.now()));
        try {
            run.recordPlanRevisions(objectMapper.writeValueAsString(revisions));
        } catch (Exception exception) {
            throw new IllegalStateException("기존 계획 버전을 저장하지 못했습니다.", exception);
        }
        run.queue();
        runRepository.saveAndFlush(run);
        eventPublisher.publishEvent(new AgentRunRevisionQueuedEvent(
                projectId, runId, instruction.strip()));
        return AgentRunResponse.from(run);
    }

    private List<AgentPlanRevision> revisions(AgentRun run) {
        if (run.getPlanRevisionsJson() == null || run.getPlanRevisionsJson().isBlank()) {
            return new ArrayList<>();
        }
        try {
            var type = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, AgentPlanRevision.class);
            return new ArrayList<>(objectMapper.readValue(run.getPlanRevisionsJson(), type));
        } catch (Exception exception) {
            throw new IllegalStateException("계획 버전 기록을 읽지 못했습니다.", exception);
        }
    }
}
