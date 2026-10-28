package com.localflow.domain.agent.domain;

import java.util.List;

public record AgentInspectionResult(String summary, String response, List<String> findings) {
    public AgentInspectionResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public AgentPlan toPlan() {
        String detail = response;
        if (!findings.isEmpty()) {
            detail += "\n\n주요 근거:\n- " + String.join("\n- ", findings);
        }
        return new AgentPlan(summary, detail, List.of());
    }
}
