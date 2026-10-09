package com.localflow.domain.provider.domain;

import java.util.List;

public record TaskScope(String instruction, List<String> tags) {
    public TaskScope {
        instruction = instruction == null ? "" : instruction.strip();
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
