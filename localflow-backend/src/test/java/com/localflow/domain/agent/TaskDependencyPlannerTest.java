package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.agent.domain.DecomposedTask;
import com.localflow.domain.agent.service.TaskDependencyPlanner;
import com.localflow.global.error.CustomException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskDependencyPlannerTest {
    private final TaskDependencyPlanner planner = new TaskDependencyPlanner();

    @Test
    void ordersChainAndBranchDependenciesStably() {
        DecomposedTask first = task("task-1", List.of());
        DecomposedTask second = task("task-2", List.of("task-1"));
        DecomposedTask third = task("task-3", List.of("task-1"));
        DecomposedTask fourth = task("task-4", List.of("task-2", "task-3"));

        assertThat(planner.order(List.of(fourth, third, second, first)))
                .extracting(DecomposedTask::id)
                .containsExactly("task-1", "task-3", "task-2", "task-4");
    }

    @Test
    void rejectsMissingDuplicateAndCyclicDependencies() {
        assertThatThrownBy(() -> planner.order(List.of(task("task-1", List.of("missing")))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> planner.order(List.of(task("task-1", List.of()), task("task-1", List.of()))))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> planner.order(List.of(
                task("task-1", List.of("task-2")), task("task-2", List.of("task-1")))))
                .isInstanceOf(CustomException.class);
    }

    private DecomposedTask task(String id, List<String> dependencies) {
        return new DecomposedTask(id, id + " 실행", dependencies, List.of(), List.of());
    }
}
