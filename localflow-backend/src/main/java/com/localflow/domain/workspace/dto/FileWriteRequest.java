package com.localflow.domain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileWriteRequest(
        @NotBlank @Size(max = 1000) String relativePath,
        @Size(max = 500000) String content,
        boolean approved
) {
}
