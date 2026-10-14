package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentInputSnapshot(
        List<String> contextFiles,
        List<MemoryReference> memories,
        String systemPrompt,
        String userPrompt,
        InputBudget budget,
        List<FileSelection> fileSelections
) {
    public AgentInputSnapshot(List<String> contextFiles, List<MemoryReference> memories,
                              String systemPrompt, String userPrompt) {
        this(contextFiles, memories, systemPrompt, userPrompt, null, List.of());
    }
    public AgentInputSnapshot(List<String> contextFiles, List<MemoryReference> memories,
                              String systemPrompt, String userPrompt, InputBudget budget) {
        this(contextFiles, memories, systemPrompt, userPrompt, budget, List.of());
    }
    public AgentInputSnapshot {
        contextFiles = contextFiles == null ? List.of() : List.copyOf(contextFiles);
        memories = memories == null ? List.of() : List.copyOf(memories);
        fileSelections = fileSelections == null ? List.of() : List.copyOf(fileSelections);
    }

    public record MemoryReference(String id, String title) { }
    public record FileSelection(String path, int score, List<String> reasons) {
        public FileSelection {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
    public record ExcludedFile(String path, String reason) { }
    public record InputBudget(
            int maxInputChars,
            int usedInputChars,
            int estimatedInputTokens,
            List<String> includedFiles,
            List<String> truncatedFiles,
            List<ExcludedFile> excludedFiles
    ) {
        public InputBudget {
            includedFiles = includedFiles == null ? List.of() : List.copyOf(includedFiles);
            truncatedFiles = truncatedFiles == null ? List.of() : List.copyOf(truncatedFiles);
            excludedFiles = excludedFiles == null ? List.of() : List.copyOf(excludedFiles);
        }
    }
}
