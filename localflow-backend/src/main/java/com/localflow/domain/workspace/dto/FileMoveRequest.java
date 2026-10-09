package com.localflow.domain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileMoveRequest(
        @NotBlank @Size(max = 1000) String destinationPath,
        boolean approved
) {
}
