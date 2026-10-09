package com.localflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.dto.ChatMessageCreateRequest;
import com.localflow.domain.chat.service.ChatMessageService;
import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.dto.ProjectMemoryCreateRequest;
import com.localflow.domain.memory.service.ProjectMemoryService;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.project.repository.ProjectRepository;
import com.localflow.domain.provider.domain.AiProviderType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectContextIntegrationTest {
    @Autowired private ProjectService projectService;
    @Autowired private ChatMessageService chatService;
    @Autowired private ProjectMemoryService memoryService;
    @Autowired private AgentRunService agentRunService;
    @Autowired private ProjectRepository projectRepository;

    @Test
    void storesProjectConversationMemoryAndAgentDraftBeforeExecution() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("localflow", null));

        chatService.create(project.id(), new ChatMessageCreateRequest(
                ChatMessageType.PROMPT, "로그인 실패 차단 기능을 추가해줘"));
        memoryService.create(project.id(), new ProjectMemoryCreateRequest(
                MemoryType.PROJECT_RULE, "예외 응답 규칙", "모든 오류는 공통 응답을 사용한다."));
        AgentRunResponse run = agentRunService.createDraft(project.id(), new AgentRunCreateRequest(
                "로그인 실패 차단 기능 구현", ExecutionMode.CONFIRM_EVERY_STEP, AiProviderType.OLLAMA));

        assertThat(chatService.findAll(project.id())).hasSize(1);
        assertThat(memoryService.findAll(project.id(), true)).hasSize(1);
        assertThat(agentRunService.findAll(project.id())).extracting("runId").contains(run.runId());
        assertThat(run.notice()).contains("실행 전 초안");
    }

    @Test
    void deletesProjectWithItsDependentLocalData() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("delete-me", null));
        chatService.create(project.id(), new ChatMessageCreateRequest(ChatMessageType.NOTE, "temporary"));

        projectService.delete(project.id());
        projectRepository.flush();

        assertThat(projectRepository.existsById(project.id())).isFalse();
    }
}
