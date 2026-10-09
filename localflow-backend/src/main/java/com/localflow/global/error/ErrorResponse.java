package com.localflow.global.error;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        boolean success,
        String code,
        String message,
        List<ValidationError> errors,
        Instant timestamp
) {
    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.name(), errorCode.message(), List.of(), Instant.now());
    }

    public static ErrorResponse validation(List<ValidationError> errors) {
        return new ErrorResponse(
                false,
                ErrorCode.INVALID_REQUEST.name(),
                ErrorCode.INVALID_REQUEST.message(),
                errors,
                Instant.now()
        );
    }
}
