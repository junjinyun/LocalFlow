package com.localflow.domain.agent.domain;

import java.util.List;
import java.util.regex.Pattern;

public record AgentInspectionResult(String summary, String response, List<String> findings) {
    private static final Pattern EVIDENCE_HEADING = Pattern.compile(
            "(?im)^\\s*(?:\\*{1,2})?(?:주요\\s*)?근거\\s*:(?:\\*{1,2})?\\s*$");

    public AgentInspectionResult {
        findings = findings == null ? List.of() : findings.stream()
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public AgentPlan toPlan() {
        String detail = normalizedResponse();
        if (!findings.isEmpty()) {
            detail += "\n\n주요 근거:\n- " + String.join("\n- ", findings);
        }
        return new AgentPlan(summary, detail, List.of());
    }

    private String normalizedResponse() {
        if (findings.isEmpty()) return response.strip();
        var matcher = EVIDENCE_HEADING.matcher(response);
        if (!matcher.find()) return response.strip();
        String normalized = response.substring(0, matcher.start()).strip();
        return normalized.isBlank() ? summary : normalized;
    }
}
