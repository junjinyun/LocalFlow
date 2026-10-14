package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.domain.memory.domain.MemoryCandidateStatus;
import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.dto.MemoryCandidateApproveRequest;
import com.localflow.domain.memory.dto.MemoryCandidateResponse;
import com.localflow.domain.memory.dto.ProjectMemoryCreateRequest;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.entity.ProjectMemoryCandidate;
import com.localflow.domain.memory.repository.ProjectMemoryCandidateRepository;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.memory.service.ProjectMemoryCandidateService;
import com.localflow.domain.memory.service.ProjectMemoryService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectMemoryCandidateServiceTest {
    @Mock ProjectMemoryCandidateRepository candidateRepository;
    @Mock ProjectMemoryRepository memoryRepository;
    @Mock ChatMessageRepository chatRepository;
    @Mock AgentRunRepository runRepository;
    @Mock ProjectMemoryService memoryService;
    @Mock ProjectService projectService;

    private ProjectMemoryCandidateService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectMemoryCandidateService(candidateRepository, memoryRepository,
                chatRepository, runRepository, memoryService, projectService);
        project = Project.create("candidate-test", null);
    }

    @Test
    void extractsExplicitConversationRuleAsPendingCandidateOnlyOnce() {
        when(projectService.requireProject(project.getId())).thenReturn(project);
        ChatMessage message = ChatMessage.create(project, ChatMessageType.PROMPT,
                "앞으로 모든 API 응답은 ApiResponse 형식을 항상 사용하도록 기억해줘.");
        when(chatRepository.findAllByProject_IdOrderByCreatedAtAsc(project.getId())).thenReturn(List.of(message));
        when(runRepository.findAllByProject_IdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(candidateRepository.existsByProject_IdAndSourceTypeAndSourceIdAndSourceOrder(
                project.getId(), MemoryCandidateSourceType.CHAT_MESSAGE, message.getId(), 0)).thenReturn(false);
        when(candidateRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<MemoryCandidateResponse> result = service.extract(project.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo(MemoryCandidateStatus.PENDING);
        assertThat(result.get(0).type()).isEqualTo(MemoryType.PROJECT_RULE);
        verify(memoryService, never()).create(any(), any(ProjectMemoryCreateRequest.class));
    }

    @Test
    void editedCandidateBecomesMemoryOnlyAfterApproval() {
        ProjectMemoryCandidate candidate = ProjectMemoryCandidate.create(project,
                MemoryCandidateSourceType.CHAT_MESSAGE, "message-id", 0, "원문",
                MemoryType.NOTE, "후보", "후보 내용", true,
                Set.of(), Set.of(), Set.of(), null);
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        ProjectMemoryResponse approved = new ProjectMemoryResponse("memory-id", project.getId(),
                MemoryType.PROJECT_RULE, "편집된 규칙", "편집된 내용", true, true,
                Set.of(), Set.of(), Set.of(), Instant.now(), Instant.now());
        when(memoryService.create(any(), any(ProjectMemoryCreateRequest.class))).thenReturn(approved);

        ProjectMemoryResponse result = service.approve(project.getId(), candidate.getId(),
                new MemoryCandidateApproveRequest(MemoryType.PROJECT_RULE, "편집된 규칙",
                        "편집된 내용", true, Set.of(), Set.of(), Set.of()));

        assertThat(result.id()).isEqualTo("memory-id");
        assertThat(candidate.getStatus()).isEqualTo(MemoryCandidateStatus.APPROVED);
        assertThat(candidate.getApprovedMemoryId()).isEqualTo("memory-id");
        verify(memoryService).create(any(), any(ProjectMemoryCreateRequest.class));
    }
}
