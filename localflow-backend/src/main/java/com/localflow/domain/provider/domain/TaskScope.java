package com.localflow.domain.provider.domain;

import java.util.List;

public record TaskScope(String instruction, List<String> tags, List<String> targetPaths) {
    public TaskScope(String instruction, List<String> tags) {
        this(instruction, tags, List.of());
    }

    public TaskScope {
        instruction = instruction == null ? "" : instruction.strip();
        tags = tags == null ? List.of() : List.copyOf(tags);
        targetPaths = targetPaths == null ? List.of() : List.copyOf(targetPaths);
    }
}
