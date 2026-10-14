package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentRunRevisionQueuedEvent;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunRevisionService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.global.error.CustomException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AgentRunRevisionServiceTest {
    private final AgentRunService runService = mock(AgentRunService.class);
    private final AgentRunRepository repository = mock(AgentRunRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AgentRunRevisionService service = new AgentRunRevisionService(
            runService, repository, publisher, objectMapper);

    @Test
    void archivesCurrentPlanAndQueuesRevision() throws Exception {
        Project project = Project.create("revision", null);
        AgentRun run = AgentRun.draft(project, "로그인 추가", ExecutionMode.BALANCED,
                AiProviderType.OLLAMA, "qwen");
        run.waitForApproval("{}", "{\"operations\":[]}", "[]", "승인 필요", "qwen", 10, 2);
        when(runService.requireRunForUpdate(project.getId(), run.getId())).thenReturn(run);
        when(repository.saveAndFlush(any(AgentRun.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.request(project.getId(), run.getId(), "테스트도 추가해줘");

        assertThat(response.status()).isEqualTo(AgentRunStatus.PENDING);
        assertThat(run.getPlanRevisionsJson()).isNotBlank();
        assertThat(objectMapper.readTree(run.getPlanRevisionsJson()).get(0).path("revisionInstruction").asText())
                .isEqualTo("테스트도 추가해줘");
        verify(publisher).publishEvent(any(AgentRunRevisionQueuedEvent.class));
    }

    @Test
    void rejectsRevisionOutsideApprovalState() {
        Project project = Project.create("revision", null);
        AgentRun run = AgentRun.draft(project, "로그인 추가", ExecutionMode.BALANCED,
                AiProviderType.OLLAMA, "qwen");
        when(runService.requireRunForUpdate(project.getId(), run.getId())).thenReturn(run);

        assertThatThrownBy(() -> service.request(project.getId(), run.getId(), "수정"))
                .isInstanceOf(CustomException.class);
        verify(publisher, never()).publishEvent(any());
    }
}
