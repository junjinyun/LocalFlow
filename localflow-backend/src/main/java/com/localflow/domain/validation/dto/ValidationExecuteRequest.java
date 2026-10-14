package com.localflow.domain.validation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ValidationExecuteRequest(
        @NotBlank String commandId,
        @AssertTrue(message = "검증 명령을 확인한 후 승인해야 합니다.") boolean approved
) {
}
