package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.service.AgentPlanParser;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.global.error.CustomException;
import org.junit.jupiter.api.Test;

class AgentPlanParserTest {
    private final AgentPlanParser parser = new AgentPlanParser(new ObjectMapper(),
            new AiProviderProperties(60_000, 2, null, null, null, null));

    @Test
    void parsesJsonPlanEvenWhenModelWrapsItInCodeFence() {
        var plan = parser.parse("""
                ```json
                {"summary":"설정 추가","response":"완료","operations":[
                  {"action":"CREATE","path":"src/config.yml","destinationPath":null,"content":"enabled: true"}
                ]}
                ```
                """);

        assertThat(plan.summary()).isEqualTo("설정 추가");
        assertThat(plan.operations()).hasSize(1);
        assertThat(plan.operations().get(0).action()).isEqualTo(FileOperationAction.CREATE);
        assertThat(plan.operations().get(0).path()).isEqualTo("src/config.yml");
    }

    @Test
    void parsesUpdatePlanWithCompleteFileContent() {
        var plan = parser.parse("""
                {"summary":"설정 수정","response":"완료","operations":[
                  {"action":"UPDATE","path":"src/config.yml","destinationPath":null,"content":"enabled: false"}
                ]}
                """);

        assertThat(plan.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.action()).isEqualTo(FileOperationAction.UPDATE);
            assertThat(operation.path()).isEqualTo("src/config.yml");
            assertThat(operation.content()).isEqualTo("enabled: false");
        });
    }

    @Test
    void parsesNoChangePlanWithEmptyOperations() {
        var plan = parser.parse("""
                {"summary":"변경 불필요","response":"이미 요청 상태입니다.","operations":[]}
                """);

        assertThat(plan.operations()).isEmpty();
        assertThat(plan.response()).isEqualTo("이미 요청 상태입니다.");
    }

    @Test
    void rejectsPlanOverConfiguredOperationLimit() {
        assertThatThrownBy(() -> parser.parse("""
                {"summary":"too many","response":"no","operations":[
                  {"action":"DELETE","path":"a.txt"},
                  {"action":"DELETE","path":"b.txt"},
                  {"action":"DELETE","path":"c.txt"}
                ]}
                """)).isInstanceOf(CustomException.class);
    }
}
