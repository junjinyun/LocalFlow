package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProjectMemoryUpdateRequest(
        @NotNull MemoryType type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 10000) String content,
        boolean active
) {
}
