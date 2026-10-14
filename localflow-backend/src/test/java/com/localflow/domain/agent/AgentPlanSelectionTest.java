package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.agent.service.AgentPlanSelection;
import com.localflow.global.error.CustomException;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentPlanSelectionTest {
    private final AgentPlan plan = new AgentPlan("요약", "응답", List.of(
            new FileOperationPlan("op-1", FileOperationAction.CREATE, "a.txt", null, "a"),
            new FileOperationPlan("op-2", FileOperationAction.UPDATE, "b.txt", null, "b"),
            new FileOperationPlan("op-3", FileOperationAction.MOVE, "c.txt", "d.txt", null),
            new FileOperationPlan("op-4", FileOperationAction.DELETE, "e.txt", null, null)
    ));

    @Test
    void selectsOnlyRequestedOperationsInOriginalOrder() {
        AgentPlan selected = AgentPlanSelection.select(plan, List.of("op-4", "op-2"));

        assertThat(selected.operations()).extracting(FileOperationPlan::id)
                .containsExactly("op-2", "op-4");
    }

    @Test
    void rejectsEmptyDuplicateAndUnknownSelections() {
        assertThatThrownBy(() -> AgentPlanSelection.select(plan, List.of()))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> AgentPlanSelection.select(plan, List.of("op-1", "op-1")))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> AgentPlanSelection.select(plan, List.of("unknown")))
                .isInstanceOf(CustomException.class);
    }
}
