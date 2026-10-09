package com.localflow.domain.chat.dto;

import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.entity.ChatMessage;
import java.time.Instant;

public record ChatMessageResponse(
        String id, String projectId, ChatMessageType type, String content, Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getProject().getId(),
                message.getType(), message.getContent(), message.getCreatedAt());
    }
}
