package com.localflow.domain.agent.domain;

import java.util.List;

public record TaskDecomposition(String summary, String source, List<DecomposedTask> tasks) {
    public TaskDecomposition {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
