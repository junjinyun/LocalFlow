package com.localflow.domain.agent.dto.response;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.provider.domain.AiProviderType;
import java.time.Instant;
import com.localflow.domain.agent.entity.AgentRun;

public record AgentRunResponse(
        String runId,
        String projectId,
        String prompt,
        ExecutionMode executionMode,
        AiProviderType preferredGenerationProvider,
        String preferredModel,
        AiProviderType actualGenerationProvider,
        AgentRunStatus status,
        String notice,
        String decompositionJson,
        String decisionJson,
        String planJson,
        String changesJson,
        String inputSnapshotJson,
        String resultMessage,
        String errorMessage,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static AgentRunResponse from(AgentRun run) {
        return new AgentRunResponse(run.getId(), run.getProject().getId(), run.getPrompt(),
                run.getExecutionMode(), run.getPreferredGenerationProvider(),
                run.getPreferredModel(), run.getActualGenerationProvider(), run.getStatus(), notice(run),
                run.getDecompositionJson(), run.getDecisionJson(), run.getPlanJson(), run.getChangesJson(),
                run.getInputSnapshotJson(), run.getResultMessage(),
                run.getErrorMessage(), run.getModel(), run.getInputTokens(), run.getOutputTokens(),
                run.getCreatedAt(), run.getUpdatedAt(), run.getCompletedAt());
    }

    public static String notice(AgentRun run) {
        return switch (run.getStatus()) {
            case DRAFT -> "실행 전 초안입니다.";
            case WAITING_APPROVAL -> "파일 작업 적용 전에 사용자 승인을 기다리고 있습니다.";
            case COMPLETED -> "AI 작업이 완료되었습니다.";
            case FAILED -> "AI 작업이 실패했습니다.";
            case CANCELLED -> "사용자가 실행을 취소했습니다.";
            default -> "AI 작업을 처리하고 있습니다.";
        };
    }
}
