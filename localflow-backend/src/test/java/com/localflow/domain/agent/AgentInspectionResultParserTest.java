package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.service.AgentInspectionResultParser;
import org.junit.jupiter.api.Test;

class AgentInspectionResultParserTest {
    private final AgentInspectionResultParser parser =
            new AgentInspectionResultParser(new ObjectMapper());

    @Test
    void parsesInspectionResponseWithoutFileOperationShape() {
        var result = parser.parse("""
                {"summary":"인증 구조 분석","response":"JWT 필터를 사용합니다.",
                 "findings":["SecurityConfig가 필터를 등록합니다.","JwtProvider가 토큰을 검증합니다."]}
                """);

        assertThat(result.summary()).isEqualTo("인증 구조 분석");
        assertThat(result.findings()).hasSize(2);
        assertThat(result.toPlan().operations()).isEmpty();
        assertThat(result.toPlan().response()).contains("주요 근거", "SecurityConfig");
    }

    @Test
    void acceptsMissingFindingsForProviderCompatibility() {
        var result = parser.parse("{\"summary\":\"분석\",\"response\":\"결과\"}");

        assertThat(result.findings()).isEmpty();
    }
}
