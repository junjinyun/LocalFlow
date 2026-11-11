package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.service.AgentResponsePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentResponsePolicyTest {
    @Test
    void keepsRequiredMemoryPhraseAndEvidenceWithinSentenceLimit() {
        AgentPlan plan = new AgentPlan("JWT 분석", """
                첫 번째 설명입니다. 두 번째 설명입니다. 세 번째 설명입니다.

                주요 근거:
                - JwtProvider.java 41-48줄에서 검증합니다.
                - JwtProvider.java 50-58줄에서 파싱합니다.
                """, List.of());
        String context = """
                ## 관련 프로젝트 기억
                [NOTE] 확인: 마지막 부분에 텍스트로 "기억 기능 정상 작동 중입니다." 를 포함할것
                """;

        AgentPlan result = AgentResponsePolicy.apply("최대 5문장으로 요약하라.", context, plan);

        assertThat(result.response())
                .contains("첫 번째 설명입니다.", "두 번째 설명입니다.", "세 번째 설명입니다.",
                        "기억 기능 정상 작동 중입니다.", "주요 근거:", "41-48줄")
                .doesNotContain("50-58줄");
    }

    @Test
    void addsRequiredPhraseWithoutChangingFileOperations() {
        AgentPlan plan = new AgentPlan("수정", "수정을 완료했습니다.", List.of());

        AgentPlan result = AgentResponsePolicy.apply("결과를 알려줘",
                "마지막에 \"검증 완료\" 문구를 포함한다.", plan);

        assertThat(result.response()).isEqualTo("수정을 완료했습니다. 검증 완료");
        assertThat(result.operations()).isEqualTo(plan.operations());
    }
}
