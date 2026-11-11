package com.localflow.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.dto.ChatMessageCreateRequest;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.chat.service.ChatMessageService;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    @Mock ChatMessageRepository repository;
    @Mock ProjectService projectService;
    @Mock AgentRunRepository runRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private ChatMessageService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ChatMessageService(repository, projectService, runRepository, eventPublisher);
        project = Project.create("chat-memory-test", null);
        when(projectService.requireProject(project.getId())).thenReturn(project);
        when(repository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void publishesCandidateExtractionAfterUserMessageIsSaved() {
        var response = service.create(project.getId(), new ChatMessageCreateRequest(
                ChatMessageType.PROMPT, "앞으로 API 응답은 항상 공통 형식을 사용한다."));

        ArgumentCaptor<MemoryCandidateExtractionRequestedEvent> captor =
                ArgumentCaptor.forClass(MemoryCandidateExtractionRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(project.getId());
        assertThat(captor.getValue().sourceType()).isEqualTo(MemoryCandidateSourceType.CHAT_MESSAGE);
        assertThat(captor.getValue().sourceId()).isEqualTo(response.id());
        assertThat(captor.getValue().content()).isEqualTo(response.content());
    }

    @Test
    void doesNotExtractCandidateFromSystemMessage() {
        service.create(project.getId(), new ChatMessageCreateRequest(
                ChatMessageType.SYSTEM, "AI 실행 완료"));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
