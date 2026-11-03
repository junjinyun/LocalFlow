package com.localflow.domain.chat.dto;

import com.localflow.domain.chat.domain.ChatMessageType;
import com.localflow.domain.chat.entity.ChatMessage;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import java.time.Instant;

public record ChatMessageResponse(
        String id, String projectId, ChatMessageType type, String content,
        String relatedRunId, AgentRunStatus relatedRunStatus, String relatedRunNotice,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message, AgentRun run) {
        return new ChatMessageResponse(message.getId(), message.getProject().getId(),
                message.getType(), message.getContent(), run == null ? null : run.getId(),
                run == null ? null : run.getStatus(),
                run == null ? null : AgentRunResponse.notice(run), message.getCreatedAt());
    }
}
