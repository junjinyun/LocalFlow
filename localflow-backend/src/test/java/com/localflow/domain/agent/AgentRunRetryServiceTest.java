package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.domain.AgentRunQueuedEvent;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunRetryService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AgentRunRetryServiceTest {
    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentRunRepository repository = mock(AgentRunRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AgentRunRetryService service = new AgentRunRetryService(runService, repository, publisher);
    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.create("retry-test", null);
    }

    @Test
    void createsQueuedRunWithSameInputAndFreshApprovalFlow() {
        AgentRun failed = retryableFailedRun();
        when(runService.requireRunForUpdate(project.getId(), failed.getId())).thenReturn(failed);
        when(runService.hasOtherActiveRun(project.getId(), failed.getId())).thenReturn(false);
        when(repository.saveAndFlush(any(AgentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.retry(project.getId(), failed.getId());

        assertThat(response.status()).isEqualTo(AgentRunStatus.PENDING);
        assertThat(response.prompt()).isEqualTo(failed.getPrompt());
        assertThat(response.retryOfRunId()).isEqualTo(failed.getId());
        assertThat(failed.getRetriedByRunId()).isEqualTo(response.runId());
        verify(publisher).publishEvent(any(AgentRunQueuedEvent.class));
    }

    @Test
    void rejectsPermanentProviderFailure() {
        AgentRun failed = AgentRun.draft(project, "실행", ExecutionMode.BALANCED,
                AiProviderType.OPENAI, "gpt-5-mini");
        failed.failProvider("인증 실패", ProviderErrorCode.AUTHENTICATION_FAILED, false, "키 확인");
        when(runService.requireRunForUpdate(project.getId(), failed.getId())).thenReturn(failed);

        assertThatThrownBy(() -> service.retry(project.getId(), failed.getId()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.AGENT_RUN_NOT_RETRYABLE));
        verify(publisher, never()).publishEvent(any());
    }

    private AgentRun retryableFailedRun() {
        AgentRun run = AgentRun.draft(project, "로그인 오류 수정", ExecutionMode.BALANCED,
                AiProviderType.OPENAI, "gpt-5-mini");
        run.failProvider("요청 제한", ProviderErrorCode.RATE_LIMITED, true, "잠시 후 재시도");
        return run;
    }
}
