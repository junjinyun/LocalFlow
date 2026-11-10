package com.localflow.domain.agent.domain;

import java.util.List;
import java.util.ArrayList;

public record AgentInputSnapshot(
        List<String> contextFiles,
        List<MemoryReference> memories,
        String systemPrompt,
        String userPrompt,
        InputBudget budget,
        List<FileSelection> fileSelections,
        List<TaskInputSnapshot> taskSnapshots
) {
    public AgentInputSnapshot(List<String> contextFiles, List<MemoryReference> memories,
                              String systemPrompt, String userPrompt) {
        this(contextFiles, memories, systemPrompt, userPrompt, null, List.of(), List.of());
    }
    public AgentInputSnapshot(List<String> contextFiles, List<MemoryReference> memories,
                              String systemPrompt, String userPrompt, InputBudget budget) {
        this(contextFiles, memories, systemPrompt, userPrompt, budget, List.of(), List.of());
    }
    public AgentInputSnapshot(List<String> contextFiles, List<MemoryReference> memories,
                              String systemPrompt, String userPrompt, InputBudget budget,
                              List<FileSelection> fileSelections) {
        this(contextFiles, memories, systemPrompt, userPrompt, budget, fileSelections, List.of());
    }
    public AgentInputSnapshot {
        contextFiles = contextFiles == null ? List.of() : List.copyOf(contextFiles);
        memories = memories == null ? List.of() : List.copyOf(memories);
        fileSelections = fileSelections == null ? List.of() : List.copyOf(fileSelections);
        taskSnapshots = taskSnapshots == null ? List.of() : List.copyOf(taskSnapshots);
    }

    public AgentInputSnapshot appendTaskSnapshot(String taskId, int order, String instruction,
                                                  AgentInputSnapshot snapshot) {
        List<TaskInputSnapshot> updated = new ArrayList<>(taskSnapshots);
        updated.removeIf(item -> item.taskId().equals(taskId));
        AgentInputSnapshot taskInput = new AgentInputSnapshot(snapshot.contextFiles(), snapshot.memories(),
                snapshot.systemPrompt(), snapshot.userPrompt(), snapshot.budget(),
                snapshot.fileSelections(), List.of());
        updated.add(new TaskInputSnapshot(taskId, order, instruction, taskInput));
        return new AgentInputSnapshot(snapshot.contextFiles(), snapshot.memories(),
                snapshot.systemPrompt(), snapshot.userPrompt(), snapshot.budget(),
                snapshot.fileSelections(), updated);
    }

    public record MemoryReference(String id, String title) { }
    public record FileSelection(String path, int score, List<String> reasons) {
        public FileSelection {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }
    public record ExcludedFile(String path, String reason) { }
    public record TaskInputSnapshot(String taskId, int order, String instruction,
                                    AgentInputSnapshot input) { }
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
