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
        String rawJson
) {
    public DecisionResult {
        selectedTags = selectedTags == null ? List.of() : List.copyOf(selectedTags);
        taskScopes = taskScopes == null ? List.of() : List.copyOf(taskScopes);
    }

    public static DecisionResult fallback(String actionType, String targetPath,
                                          List<TaskScope> taskScopes,
                                          String riskLevel, boolean needsHumanApproval) {
        List<String> tags = taskScopes.stream().flatMap(scope -> scope.tags().stream()).distinct().toList();
        return new DecisionResult(actionType, targetPath, tags, taskScopes, riskLevel,
                needsHumanApproval, "LOCAL_HEURISTIC", null);
    }
}
