package com.localflow.domain.provider.domain;

import java.util.List;

public record DecisionRequest(
        String state,
        List<String> candidateFiles,
        List<String> candidateTags,
        List<String> taskUnits
) {
    public DecisionRequest {
        candidateFiles = candidateFiles == null ? List.of() : List.copyOf(candidateFiles);
        candidateTags = candidateTags == null ? List.of() : List.copyOf(candidateTags);
        taskUnits = taskUnits == null ? List.of() : List.copyOf(taskUnits);
    }
}
