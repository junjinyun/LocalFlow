package com.localflow.domain.agent.domain;

import java.util.List;

public record DecomposedTask(
        String id,
        String instruction,
        List<String> dependsOn,
        List<String> suggestedTags,
        List<String> acceptanceCriteria
) {
    public DecomposedTask {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        suggestedTags = suggestedTags == null ? List.of() : List.copyOf(suggestedTags);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    }
}
