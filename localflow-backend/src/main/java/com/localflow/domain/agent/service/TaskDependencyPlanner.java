package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.DecomposedTask;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.springframework.stereotype.Component;

@Component
public class TaskDependencyPlanner {
    public List<DecomposedTask> order(List<DecomposedTask> tasks) {
        Map<String, DecomposedTask> byId = new LinkedHashMap<>();
        for (DecomposedTask task : tasks) {
            if (task.id() == null || task.id().isBlank() || byId.put(task.id(), task) != null) {
                throw invalid();
            }
        }
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        byId.keySet().forEach(id -> indegree.put(id, 0));
        for (DecomposedTask task : tasks) {
            for (String dependency : task.dependsOn()) {
                if (!byId.containsKey(dependency) || dependency.equals(task.id())) throw invalid();
                indegree.compute(task.id(), (id, count) -> count + 1);
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(task.id());
            }
        }
        Queue<String> ready = new ArrayDeque<>();
        indegree.forEach((id, count) -> { if (count == 0) ready.add(id); });
        List<DecomposedTask> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(byId.get(id));
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                int remaining = indegree.compute(dependent, (key, count) -> count - 1);
                if (remaining == 0) ready.add(dependent);
            }
        }
        if (ordered.size() != tasks.size()) throw invalid();
        return List.copyOf(ordered);
    }

    private CustomException invalid() {
        return new CustomException(ErrorCode.INVALID_TASK_DEPENDENCY);
    }
}
