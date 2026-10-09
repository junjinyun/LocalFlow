package com.localflow.domain.chat.dto;

import com.localflow.domain.chat.domain.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChatMessageCreateRequest(
        @NotNull ChatMessageType type,
        @NotBlank @Size(max = 20000) String content
) {
}
