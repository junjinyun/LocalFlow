package com.localflow.domain.provider.domain;

import java.util.List;

public record DecisionResult(
        String actionType,
        String targetPath,
        List<String> selectedTags,
        List<TaskScope> taskScopes,
        String riskLevel,
        boolean needsHumanApproval,
        String model,
        String rawJson,
        boolean fallbackUsed,
        String fallbackReason,
        ProviderErrorCode fallbackProviderErrorCode
) {
    public DecisionResult(String actionType, String targetPath, List<String> selectedTags,
                          List<TaskScope> taskScopes, String riskLevel,
                          boolean needsHumanApproval, String model, String rawJson) {
        this(actionType, targetPath, selectedTags, taskScopes, riskLevel,
                needsHumanApproval, model, rawJson, false, null, null);
    }
    public DecisionResult {
        selectedTags = selectedTags == null ? List.of() : List.copyOf(selectedTags);
        taskScopes = taskScopes == null ? List.of() : List.copyOf(taskScopes);
    }

    public static DecisionResult fallback(String actionType, String targetPath,
                                          List<TaskScope> taskScopes,
                                          String riskLevel, boolean needsHumanApproval) {
        List<String> tags = taskScopes.stream().flatMap(scope -> scope.tags().stream()).distinct().toList();
        return fallback(actionType, targetPath, taskScopes, riskLevel, needsHumanApproval,
                "JEV를 사용하지 않아 로컬 휴리스틱으로 판단했습니다.", null);
    }

    public static DecisionResult fallback(String actionType, String targetPath,
                                          List<TaskScope> taskScopes,
                                          String riskLevel, boolean needsHumanApproval,
                                          String reason, ProviderErrorCode errorCode) {
        List<String> tags = taskScopes.stream().flatMap(scope -> scope.tags().stream()).distinct().toList();
        return new DecisionResult(actionType, targetPath, tags, taskScopes, riskLevel,
                needsHumanApproval, "LOCAL_HEURISTIC", null, true, reason, errorCode);
    }
}
