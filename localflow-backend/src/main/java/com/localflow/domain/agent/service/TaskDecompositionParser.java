package com.localflow.domain.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.DecomposedTask;
import com.localflow.domain.agent.domain.TaskDecomposition;
import com.localflow.domain.provider.domain.TaskScope;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TaskDecompositionParser {
    private static final int MAX_TASKS = 5;
    private final ObjectMapper objectMapper;
    private final PromptTaskDecomposer localDecomposer;

    public TaskDecompositionParser(ObjectMapper objectMapper, PromptTaskDecomposer localDecomposer) {
        this.objectMapper = objectMapper;
        this.localDecomposer = localDecomposer;
    }

    public TaskDecomposition parse(String value) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(value));
            JsonNode nodes = root.path("tasks");
            if (!nodes.isArray() || nodes.isEmpty() || nodes.size() > MAX_TASKS) {
                throw new IllegalArgumentException("invalid tasks");
            }
            List<DecomposedTask> tasks = new ArrayList<>();
            Set<String> knownIds = new LinkedHashSet<>();
            for (int index = 0; index < nodes.size(); index++) {
                JsonNode node = nodes.get(index);
                String id = text(node, "id", "task-" + (index + 1));
                if (knownIds.contains(id)) id = "task-" + (index + 1);
                while (knownIds.contains(id)) id = id + "-next";
                knownIds.add(id);
                String taskId = id;
                String instruction = text(node, "instruction", null);
                if (instruction == null || instruction.isBlank()) throw new IllegalArgumentException("empty task");
                List<String> dependsOn = strings(node.path("dependsOn")).stream()
                        .filter(knownIds::contains).filter(valueId -> !valueId.equals(taskId)).distinct().toList();
                tasks.add(new DecomposedTask(taskId, instruction.strip(), dependsOn,
                        strings(node.path("suggestedTags")), strings(node.path("acceptanceCriteria"))));
            }
            return new TaskDecomposition(text(root, "summary", "복합 요청 작업 분해"),
                    text(root, "source", "AI"), tasks);
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI 작업 분해 결과를 해석하지 못했습니다.", exception);
        }
    }

    public TaskDecomposition fallback(String prompt, List<String> availableTags) {
        List<TaskScope> scopes = localDecomposer.scopes(prompt, availableTags);
        List<DecomposedTask> tasks = new ArrayList<>();
        for (int index = 0; index < scopes.size(); index++) {
            TaskScope scope = scopes.get(index);
            tasks.add(new DecomposedTask("task-" + (index + 1), scope.instruction(),
                    index == 0 ? List.of() : List.of("task-" + index), scope.tags(), List.of()));
        }
        return new TaskDecomposition("로컬 규칙 기반 작업 분해", "LOCAL_FALLBACK", tasks);
    }

    public String toJson(TaskDecomposition decomposition) {
        try {
            return objectMapper.writeValueAsString(decomposition);
        } catch (Exception exception) {
            throw new IllegalStateException("작업 분해 결과를 저장하지 못했습니다.", exception);
        }
    }

    private String extractJson(String value) {
        if (value == null) throw new IllegalArgumentException("empty response");
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("json not found");
        return value.substring(start, end + 1);
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText();
        return value.isBlank() ? fallback : value;
    }

    private List<String> strings(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            String text = value.asText().strip();
            if (!text.isBlank() && !values.contains(text)) values.add(text);
        });
        return List.copyOf(values);
    }
}
