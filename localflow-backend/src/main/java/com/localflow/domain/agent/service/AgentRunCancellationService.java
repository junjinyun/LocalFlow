package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.entity.AgentRunProgressEvent;
import com.localflow.domain.agent.repository.AgentRunProgressEventRepository;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunCancellationService {
    private static final Set<AgentRunStatus> CANCELLABLE = EnumSet.of(
            AgentRunStatus.DRAFT,
            AgentRunStatus.PENDING,
            AgentRunStatus.DECIDING,
            AgentRunStatus.PLANNING,
            AgentRunStatus.VALIDATING,
            AgentRunStatus.RUNNING,
            AgentRunStatus.WAITING_APPROVAL
    );
    private static final Set<AgentRunStatus> IMMEDIATELY_CANCELLABLE = EnumSet.of(
            AgentRunStatus.DRAFT,
            AgentRunStatus.PENDING,
            AgentRunStatus.WAITING_APPROVAL
    );

    private final AgentRunRepository runRepository;
    private final AgentRunProgressEventRepository progressRepository;
    private final Set<String> cancellationSignals = ConcurrentHashMap.newKeySet();

    public AgentRunCancellationService(AgentRunRepository runRepository,
                                       AgentRunProgressEventRepository progressRepository) {
        this.runRepository = runRepository;
        this.progressRepository = progressRepository;
    }

    @Transactional
    public AgentRunResponse request(String projectId, String runId) {
        cancellationSignals.add(runId);
        AgentRun run = requireForUpdate(projectId, runId);
        AgentRunStatus previousStatus = run.getStatus();
        if (!CANCELLABLE.contains(previousStatus)) {
            cancellationSignals.remove(runId);
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }

        run.requestCancellation();
        progressRepository.save(AgentRunProgressEvent.create(run,
                AgentProgressStage.CANCELLATION_REQUESTED,
                "사용자가 실행 취소를 요청했습니다. 진행 중인 응답과 변경 결과를 폐기합니다."));
        if (IMMEDIATELY_CANCELLABLE.contains(previousStatus)) {
            run.cancel();
            progressRepository.save(AgentRunProgressEvent.create(run,
                    AgentProgressStage.CANCELLED,
                    "에이전트 실행을 취소했습니다. 프로젝트 파일에는 변경을 적용하지 않았습니다."));
            cancellationSignals.remove(runId);
        }
        runRepository.saveAndFlush(run);
        progressRepository.flush();
        return AgentRunResponse.from(run);
    }

    public void checkpoint(String projectId, String runId) {
        if (cancellationSignals.contains(runId) || isCancellationState(projectId, runId)) {
            throw new AgentRunCancelledException();
        }
    }

    public boolean isCancellationRequested(String projectId, String runId) {
        return cancellationSignals.contains(runId) || isCancellationState(projectId, runId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String projectId, String runId) {
        AgentRun run = requireForUpdate(projectId, runId);
        if (run.getStatus() == AgentRunStatus.CANCELLED) {
            cancellationSignals.remove(runId);
            return;
        }
        if (run.getStatus() != AgentRunStatus.CANCEL_REQUESTED) {
            return;
        }
        run.cancel();
        runRepository.saveAndFlush(run);
        progressRepository.saveAndFlush(AgentRunProgressEvent.create(run,
                AgentProgressStage.CANCELLED,
                "에이전트 실행을 취소했습니다. 생성된 AI 결과는 폐기했습니다."));
        cancellationSignals.remove(runId);
    }

    private boolean isCancellationState(String projectId, String runId) {
        AgentRun run = runRepository.findDetailedById(runId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND));
        validateProject(projectId, run);
        return run.getStatus() == AgentRunStatus.CANCEL_REQUESTED
                || run.getStatus() == AgentRunStatus.CANCELLED;
    }

    private AgentRun requireForUpdate(String projectId, String runId) {
        AgentRun run = runRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND));
        validateProject(projectId, run);
        return run;
    }

    private void validateProject(String projectId, AgentRun run) {
        if (!run.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.AGENT_RUN_NOT_FOUND);
        }
    }
}
