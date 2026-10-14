package com.localflow.domain.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRunRevisionRequest(
        @NotBlank @Size(max = 4000) String instruction
) {
}
