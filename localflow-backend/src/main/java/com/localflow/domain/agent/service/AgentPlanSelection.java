package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class AgentPlanSelection {
    private AgentPlanSelection() {
    }

    public static AgentPlan select(AgentPlan original, List<String> selectedOperationIds) {
        if (original.operations().isEmpty() || selectedOperationIds == null) return original;
        if (selectedOperationIds.isEmpty()
                || selectedOperationIds.stream().anyMatch(id -> id == null || id.isBlank())
                || selectedOperationIds.stream().distinct().count() != selectedOperationIds.size()) {
            throw new CustomException(ErrorCode.INVALID_OPERATION_SELECTION);
        }
        Set<String> selected = new LinkedHashSet<>(selectedOperationIds);
        Set<String> available = original.operations().stream().map(FileOperationPlan::id)
                .collect(Collectors.toSet());
        if (!available.containsAll(selected)) {
            throw new CustomException(ErrorCode.INVALID_OPERATION_SELECTION);
        }
        List<FileOperationPlan> operations = original.operations().stream()
                .filter(operation -> selected.contains(operation.id()))
                .toList();
        return new AgentPlan(original.summary(), original.response(), operations);
    }
}
