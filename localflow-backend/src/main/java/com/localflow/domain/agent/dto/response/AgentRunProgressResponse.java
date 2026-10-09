package com.localflow.domain.agent.dto.response;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.entity.AgentRunProgressEvent;
import java.time.Instant;

public record AgentRunProgressResponse(
        Long id,
        AgentProgressStage stage,
        String message,
        Instant createdAt
) {
    public static AgentRunProgressResponse from(AgentRunProgressEvent event) {
        return new AgentRunProgressResponse(
                event.getId(), event.getStage(), event.getMessage(), event.getCreatedAt());
    }
}
