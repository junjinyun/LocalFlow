package com.localflow.domain.agent.service;

import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.dto.response.AgentRunSummaryResponse;
import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AgentRunService {
    private final ProjectService projectService;
    private final AgentRunRepository repository;
    private final AgentPlanParser planParser;

    public AgentRunService(ProjectService projectService, AgentRunRepository repository,
                           AgentPlanParser planParser) {
        this.projectService = projectService;
        this.repository = repository;
        this.planParser = planParser;
    }

    @Transactional
    public AgentRunResponse createDraft(String projectId, AgentRunCreateRequest request) {
        Project project = projectService.requireProject(projectId);
        AgentRun run = AgentRun.draft(project, request.prompt().strip(), request.executionMode(),
                request.preferredGenerationProvider(), request.preferredModel());
        return AgentRunResponse.from(repository.save(run));
    }

    public List<AgentRunSummaryResponse> findAll(String projectId) {
        projectService.requireProject(projectId);
        return repository.findAllByProject_IdOrderByCreatedAtDesc(projectId).stream()
                .map(run -> AgentRunSummaryResponse.from(run, plan(run))).toList();
    }

    public AgentRunResponse findById(String projectId, String runId) {
        return AgentRunResponse.from(requireRun(projectId, runId));
    }

    @Transactional
    public AgentRunResponse cancel(String projectId, String runId) {
        AgentRun run = requireRun(projectId, runId);
        if (run.getStatus() != com.localflow.domain.agent.domain.AgentRunStatus.DRAFT
                && run.getStatus() != com.localflow.domain.agent.domain.AgentRunStatus.PENDING
                && run.getStatus() != com.localflow.domain.agent.domain.AgentRunStatus.WAITING_APPROVAL) {
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }
        run.cancel();
        return AgentRunResponse.from(run);
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
