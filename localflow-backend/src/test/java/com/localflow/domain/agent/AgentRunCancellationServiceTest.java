package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunProgressEventRepository;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunCancellationService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRunCancellationServiceTest {
    private final AgentRunRepository runRepository = mock(AgentRunRepository.class);
    private final AgentRunProgressEventRepository progressRepository =
            mock(AgentRunProgressEventRepository.class);
    private AgentRunCancellationService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new AgentRunCancellationService(runRepository, progressRepository);
        project = Project.create("cancel-test", null);
    }

    @Test
    void pendingRunIsCancelledImmediatelyWithoutWaitingForWorker() {
        AgentRun run = draft();
        run.queue();
        stubRun(run);

        var response = service.request(project.getId(), run.getId());

        assertThat(response.status()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(response.cancelRequestedAt()).isNotNull();
        assertThat(response.cancelledAt()).isNotNull();
        verify(runRepository).saveAndFlush(run);
    }

    @Test
    void inFlightRunKeepsCancellationRequestedUntilWorkerCheckpoint() {
        AgentRun run = draft();
        run.queue();
        run.startDeciding();
        run.startPlanning(AiProviderType.OLLAMA);
        stubRun(run);

        var response = service.request(project.getId(), run.getId());

        assertThat(response.status()).isEqualTo(AgentRunStatus.CANCEL_REQUESTED);
        assertThatThrownBy(() -> service.checkpoint(project.getId(), run.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("취소");

        service.complete(project.getId(), run.getId());
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.CANCELLED);
        assertThat(run.getCancelledAt()).isNotNull();
    }

    @Test
    void completedRunCannotBeCancelledAgain() {
        AgentRun run = draft();
        run.complete("{}", "{}", "완료", "test-model", 1, 1);
        stubRun(run);

        assertThatThrownBy(() -> service.request(project.getId(), run.getId()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(ErrorCode.INVALID_RUN_STATUS));
    }

    private AgentRun draft() {
        return AgentRun.draft(project, "작업을 수행해줘", ExecutionMode.BALANCED,
                AiProviderType.OLLAMA, "qwen2.5-coder:3b");
    }

    private void stubRun(AgentRun run) {
        when(runRepository.findByIdForUpdate(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.findDetailedById(run.getId())).thenReturn(Optional.of(run));
        when(runRepository.saveAndFlush(any(AgentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
