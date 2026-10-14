package com.localflow.domain.validation.dto;

import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.domain.ValidationCommandType;
import com.localflow.domain.validation.domain.ValidationExecutionStatus;
import java.time.Instant;

public record ValidationCommandResult(
        String commandId,
        ValidationBuildTool tool,
        ValidationCommandType type,
        String workingDirectory,
        String displayName,
        String displayCommand,
        ValidationExecutionStatus status,
        Integer exitCode,
        String output,
        boolean outputTruncated,
        long durationMillis,
        Instant startedAt,
        Instant completedAt
) {
    public static ValidationCommandResult from(ValidationCommandSpec spec,
                                               ProcessExecutionOutput output,
                                               Instant startedAt, Instant completedAt) {
        ValidationExecutionStatus status = output.timedOut()
                ? ValidationExecutionStatus.TIMED_OUT
                : output.exitCode() != null && output.exitCode() == 0
                ? ValidationExecutionStatus.SUCCESS : ValidationExecutionStatus.FAILED;
        return new ValidationCommandResult(spec.id(), spec.tool(), spec.type(),
                spec.workingDirectory(), spec.displayName(), spec.displayCommand(), status,
                output.exitCode(), output.output(), output.truncated(),
                Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli()),
                startedAt, completedAt);
    }

    public record ProcessExecutionOutput(
            Integer exitCode, boolean timedOut, String output, boolean truncated
    ) {
    }
}
