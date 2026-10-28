package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentPlan;
import java.util.Locale;

public final class ReadOnlyRequestPolicy {
    private static final String[] EXPLICIT_READ_ONLY_TERMS = {
            "수정하지 마", "변경하지 마", "생성하지 마", "추가하지 마",
            "삭제하지 마", "적용하지 마", "파일 변경 없이", "코드 변경 없이",
            "읽기 전용", "분석만", "확인만", "설명만", "리뷰만", "설계만", "제안만",
            "do not modify", "do not change", "do not write", "do not create",
            "do not delete", "without changes", "read only", "read-only", "review only"
    };

    private ReadOnlyRequestPolicy() {
    }

    public static boolean isExplicitlyReadOnly(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String normalized = prompt.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String term : EXPLICIT_READ_ONLY_TERMS) {
            if (normalized.contains(term)) return true;
        }
        return false;
    }

    public static AgentPlan enforce(String prompt, String actionType, AgentPlan plan) {
        boolean inspection = "INSPECT_FILES".equals(actionType) || "ANSWER_ONLY".equals(actionType);
        if ((inspection || isExplicitlyReadOnly(prompt)) && !plan.operations().isEmpty()) {
            return new AgentPlan(plan.summary(), plan.response(), java.util.List.of());
        }
        return plan;
    }
}
