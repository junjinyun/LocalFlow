package com.localflow.domain.agent.dto.response;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import java.time.Instant;
import java.util.List;

public record AgentRunSummaryResponse(
        String runId,
        String projectId,
        String prompt,
        ExecutionMode executionMode,
        AiProviderType preferredGenerationProvider,
        String preferredModel,
        AiProviderType actualGenerationProvider,
        AgentRunStatus status,
        String notice,
        String resultSummary,
        List<FileChangeSummary> fileChanges,
        String errorMessage,
        ProviderErrorCode providerErrorCode,
        Boolean errorRetryable,
        String errorRecommendedAction,
        int providerRetryCount,
        String retryOfRunId,
        String retriedByRunId,
        String model,
        String usageSummaryJson,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static AgentRunSummaryResponse from(AgentRun run, AgentPlan plan) {
        List<FileChangeSummary> changes = plan == null ? List.of() : plan.operations().stream()
                .map(operation -> new FileChangeSummary(operation.action(), operation.path(),
                        operation.destinationPath()))
                .toList();
        String summary = run.getErrorMessage() != null ? run.getErrorMessage()
                : plan != null ? plan.summary() : AgentRunResponse.notice(run);
        return new AgentRunSummaryResponse(run.getId(), run.getProject().getId(), run.getPrompt(),
                run.getExecutionMode(), run.getPreferredGenerationProvider(),
                run.getPreferredModel(), run.getActualGenerationProvider(), run.getStatus(), AgentRunResponse.notice(run),
                summary, changes, run.getErrorMessage(), run.getProviderErrorCode(),
                run.getErrorRetryable(), run.getErrorRecommendedAction(), run.getProviderRetryCount(),
                run.getRetryOfRunId(), run.getRetriedByRunId(), run.getModel(), run.getUsageSummaryJson(),
                run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt());
    }

    public record FileChangeSummary(
            FileOperationAction action,
            String path,
            String destinationPath
    ) {
    }
}
