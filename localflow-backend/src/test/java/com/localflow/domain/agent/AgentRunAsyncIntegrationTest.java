package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.response.AgentRunProgressResponse;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunDispatchService;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.agent.service.AgentRunRecoveryService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentRunAsyncIntegrationTest {
    @Autowired private ProjectService projectService;
    @Autowired private AgentRunService runService;
    @Autowired private AgentRunDispatchService dispatchService;
    @Autowired private AgentRunProgressService progressService;
    @Autowired private AgentRunRecoveryService recoveryService;
    @Autowired private AgentRunRepository runRepository;

    @Test
    void queuesImmediatelyPreventsDuplicateAndRecordsAsyncFailure() throws Exception {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("async-failure-project", "비동기 실패 기록 테스트"));
        try {
            AgentRunResponse draft = runService.createDraft(project.id(), new AgentRunCreateRequest(
                    "설정되지 않은 외부 AI로 분석해줘",
                    ExecutionMode.AUTONOMOUS,
                    AiProviderType.OPENAI));

            AgentRunResponse queued = dispatchService.enqueue(project.id(), draft.runId(), false);

            assertThat(queued.status()).isEqualTo(AgentRunStatus.PENDING);
            assertThatThrownBy(() -> dispatchService.enqueue(project.id(), draft.runId(), false))
                    .isInstanceOfSatisfying(CustomException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ErrorCode.INVALID_RUN_STATUS));

            AgentRunResponse failed = awaitStatus(project.id(), draft.runId(), AgentRunStatus.FAILED);
            assertThat(failed.errorMessage()).isNotBlank();
            List<AgentRunProgressResponse> events = progressService.findAll(project.id(), draft.runId());
            assertThat(events).isNotEmpty();
            assertThat(events.get(events.size() - 1).stage().name()).isEqualTo("FAILED");
        } finally {
            projectService.delete(project.id());
        }
    }

    @Test
    void resumesApprovedPlanWithoutCallingGenerationProvider() throws Exception {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("async-approval-project", "승인 재개 테스트"));
        try {
            AgentRunResponse draft = runService.createDraft(project.id(), new AgentRunCreateRequest(
                    "승인된 계획을 적용해줘",
                    ExecutionMode.CONFIRM_EVERY_STEP,
                    AiProviderType.OPENAI));
            AgentRun run = runRepository.findDetailedById(draft.runId()).orElseThrow();
            run.waitForApproval("{}",
                    "{\"summary\":\"변경 없음\",\"response\":\"승인된 계획 적용 완료\",\"operations\":[]}",
                    "[]", "승인 대기", "test-model", 1, 1);
            runRepository.saveAndFlush(run);

            AgentRunResponse queued = dispatchService.enqueue(project.id(), draft.runId(), true);

            assertThat(queued.status()).isEqualTo(AgentRunStatus.PENDING);
            AgentRunResponse completed = awaitStatus(project.id(), draft.runId(), AgentRunStatus.COMPLETED);
            assertThat(completed.resultMessage()).isEqualTo("승인된 계획 적용 완료");
        } finally {
            projectService.delete(project.id());
        }
    }

    @Test
    void marksInterruptedRunAsFailedDuringRecovery() {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("async-recovery-project", "재시작 복구 테스트"));
        try {
            AgentRunResponse draft = runService.createDraft(project.id(), new AgentRunCreateRequest(
                    "중단될 작업",
                    ExecutionMode.BALANCED,
                    AiProviderType.OLLAMA));
            AgentRun run = runRepository.findDetailedById(draft.runId()).orElseThrow();
            run.queue();
            runRepository.saveAndFlush(run);

            recoveryService.failInterruptedRuns();

            AgentRunResponse recovered = runService.findById(project.id(), draft.runId());
            assertThat(recovered.status()).isEqualTo(AgentRunStatus.FAILED);
            assertThat(recovered.errorMessage()).contains("서버가 재시작");
        } finally {
            projectService.delete(project.id());
        }
    }

    @Test
    void findsActiveRunAndBlocksAnotherDraftInSameProject() {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("active-run-project", "활성 실행 복구 테스트"));
        try {
            AgentRunResponse draft = runService.createDraft(project.id(), new AgentRunCreateRequest(
                    "오래 실행될 작업",
                    ExecutionMode.BALANCED,
                    AiProviderType.OLLAMA));
            AgentRun run = runRepository.findDetailedById(draft.runId()).orElseThrow();
            run.queue();
            runRepository.saveAndFlush(run);

            assertThat(runService.findActive(project.id()))
                    .extracting(AgentRunResponse::runId)
                    .containsExactly(draft.runId());
            assertThatThrownBy(() -> runService.createDraft(project.id(), new AgentRunCreateRequest(
                    "중복 작업",
                    ExecutionMode.BALANCED,
                    AiProviderType.OLLAMA)))
                    .isInstanceOfSatisfying(CustomException.class,
                            exception -> assertThat(exception.errorCode())
                                    .isEqualTo(ErrorCode.AGENT_RUN_ALREADY_ACTIVE));
        } finally {
            projectService.delete(project.id());
        }
    }

    private AgentRunResponse awaitStatus(String projectId, String runId,
                                         AgentRunStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        AgentRunResponse current = runService.findById(projectId, runId);
        while (current.status() != expected && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
            current = runService.findById(projectId, runId);
        }
        assertThat(current.status()).isEqualTo(expected);
        return current;
    }
}
