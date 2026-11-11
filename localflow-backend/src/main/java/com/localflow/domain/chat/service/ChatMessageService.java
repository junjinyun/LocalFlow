package com.localflow.domain.chat.service;

import com.localflow.domain.chat.dto.ChatMessageCreateRequest;
import com.localflow.domain.chat.dto.ChatMessageResponse;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionRequestedEvent;
import com.localflow.domain.memory.domain.MemoryCandidateSourceType;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatMessageRepository repository;
    private final ProjectService projectService;
    private final AgentRunRepository runRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChatMessageService(ChatMessageRepository repository, ProjectService projectService,
                              AgentRunRepository runRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.projectService = projectService;
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ChatMessageResponse create(String projectId, ChatMessageCreateRequest request) {
        Project project = projectService.requireProject(projectId);
        ChatMessage message = ChatMessage.create(project, request.type(), request.content().strip());
        ChatMessage saved = repository.save(message);
        if (saved.getType() == com.localflow.domain.chat.domain.ChatMessageType.PROMPT
                || saved.getType() == com.localflow.domain.chat.domain.ChatMessageType.NOTE
                || saved.getType() == com.localflow.domain.chat.domain.ChatMessageType.HANDOFF) {
            eventPublisher.publishEvent(new MemoryCandidateExtractionRequestedEvent(
                    projectId, MemoryCandidateSourceType.CHAT_MESSAGE,
                    saved.getId(), saved.getContent()));
        }
        return ChatMessageResponse.from(saved, null);
    }

    public List<ChatMessageResponse> findAll(String projectId) {
        projectService.requireProject(projectId);
        return repository.findAllByProject_IdOrderByCreatedAtAsc(projectId).stream()
                .map(message -> ChatMessageResponse.from(message, relatedRun(projectId, message.getId())))
                .toList();
    }

    @Transactional
    public void delete(String projectId, String messageId) {
        ChatMessage message = repository.findById(messageId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!message.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
        repository.delete(message);
    }

    private AgentRun relatedRun(String projectId, String messageId) {
        return runRepository.findFirstByProject_IdAndSourceMessageIdOrderByCreatedAtDesc(
                projectId, messageId).orElse(null);
    }
}
