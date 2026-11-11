package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.service.AgentPromptPolicy;
import org.junit.jupiter.api.Test;

class AgentPromptPolicyTest {
    @Test
    void repeatsOriginalResponseConstraintsAfterProjectContext() {
        String original = "최대 5문장으로 답하고 파일을 수정하지 마라.";

        String result = AgentPromptPolicy.reinforceFinalConstraints(
                "프로젝트 맥락:\n[PROJECT_RULE] 마지막에 확인 문구 포함", original);

        assertThat(result)
                .contains("## 최종 응답 직전 필수 확인", "관련 프로젝트 기억", "필수 포함 문구",
                        "## 응답 형식 계약", "최대 5문장", "findings 각 항목")
                .endsWith(original);
        assertThat(result.lastIndexOf(original))
                .isGreaterThan(result.indexOf("프로젝트 맥락"));
    }
}
