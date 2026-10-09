package com.localflow.domain.agent.dto.request;

import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.provider.domain.AiProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AgentRunCreateRequest(
        @NotBlank(message = "작업 프롬프트는 필수입니다.")
        @Size(max = 10000, message = "작업 프롬프트는 10,000자 이하여야 합니다.")
        String prompt,
        @NotNull(message = "실행 모드는 필수입니다.")
        ExecutionMode executionMode,
        AiProviderType preferredGenerationProvider,
        @Size(max = 150, message = "모델명은 150자 이하여야 합니다.")
        String preferredModel
) {
    public AgentRunCreateRequest(String prompt, ExecutionMode executionMode,
                                 AiProviderType preferredGenerationProvider) {
        this(prompt, executionMode, preferredGenerationProvider, null);
    }
}
