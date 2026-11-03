package com.localflow.domain.agent.service;

import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.dto.response.AgentRunSummaryResponse;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AgentRunService {
    public static final List<AgentRunStatus> ACTIVE_STATUSES = List.of(
            AgentRunStatus.PENDING,
            AgentRunStatus.DECIDING,
            AgentRunStatus.PLANNING,
            AgentRunStatus.VALIDATING,
            AgentRunStatus.RUNNING,
            AgentRunStatus.WAITING_APPROVAL,
            AgentRunStatus.CANCEL_REQUESTED
    );

    private final ProjectService projectService;
    private final AgentRunRepository repository;
    private final AgentPlanParser planParser;
    private final ChatMessageRepository chatMessageRepository;

    public AgentRunService(ProjectService projectService, AgentRunRepository repository,
                           AgentPlanParser planParser,
                           ChatMessageRepository chatMessageRepository) {
        this.projectService = projectService;
        this.repository = repository;
        this.planParser = planParser;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public AgentRunResponse createDraft(String projectId, AgentRunCreateRequest request) {
        Project project = projectService.requireProject(projectId);
        if (repository.existsByProject_IdAndStatusIn(projectId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.AGENT_RUN_ALREADY_ACTIVE);
        }
        AgentRun run = AgentRun.draft(project, request.prompt().strip(), request.executionMode(),
                request.preferredGenerationProvider(), request.preferredModel());
        if (request.sourceMessageId() != null && !request.sourceMessageId().isBlank()) {
            ChatMessage message = chatMessageRepository.findById(request.sourceMessageId())
                    .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
            if (!message.getProject().getId().equals(projectId)) {
                throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
            }
            run.linkSourceMessage(message.getId());
        }
        return AgentRunResponse.from(repository.save(run));
    }

    public List<AgentRunSummaryResponse> findAll(String projectId) {
        projectService.requireProject(projectId);
        return repository.findAllByProject_IdOrderByCreatedAtDesc(projectId).stream()
                .map(run -> AgentRunSummaryResponse.from(run, plan(run))).toList();
    }

    public List<AgentRunResponse> findActive(String projectId) {
        projectService.requireProject(projectId);
        return repository.findAllByProject_IdAndStatusInOrderByCreatedAtDesc(
                        projectId, ACTIVE_STATUSES).stream()
                .map(AgentRunResponse::from)
                .toList();
    }

    public boolean hasOtherActiveRun(String projectId, String runId) {
        return repository.existsByProject_IdAndStatusInAndIdNot(
                projectId, ACTIVE_STATUSES, runId);
    }

    public AgentRunResponse findById(String projectId, String runId) {
        return AgentRunResponse.from(requireRun(projectId, runId));
    }

    public AgentRun requireRun(String projectId, String runId) {
        AgentRun run = repository.findDetailedById(runId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND));
        validateProject(projectId, run);
        return run;
    }

    public AgentRun requireRunForUpdate(String projectId, String runId) {
        AgentRun run = repository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND));
        validateProject(projectId, run);
        return run;
    }

    private void validateProject(String projectId, AgentRun run) {
        if (!run.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }
    }

    private AgentPlan plan(AgentRun run) {
        if (run.getPlanJson() == null || run.getPlanJson().isBlank()) {
            return null;
        }
        try {
            return planParser.parse(run.getPlanJson());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
