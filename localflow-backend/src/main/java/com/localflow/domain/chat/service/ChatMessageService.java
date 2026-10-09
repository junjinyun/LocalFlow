package com.localflow.domain.chat.service;

import com.localflow.domain.chat.dto.ChatMessageCreateRequest;
import com.localflow.domain.chat.dto.ChatMessageResponse;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ChatMessageService {
    private final ChatMessageRepository repository;
    private final ProjectService projectService;

    public ChatMessageService(ChatMessageRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    @Transactional
    public ChatMessageResponse create(String projectId, ChatMessageCreateRequest request) {
        Project project = projectService.requireProject(projectId);
        ChatMessage message = ChatMessage.create(project, request.type(), request.content().strip());
        return ChatMessageResponse.from(repository.save(message));
    }

    public List<ChatMessageResponse> findAll(String projectId) {
        projectService.requireProject(projectId);
        return repository.findAllByProject_IdOrderByCreatedAtAsc(projectId).stream()
                .map(ChatMessageResponse::from).toList();
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
}
