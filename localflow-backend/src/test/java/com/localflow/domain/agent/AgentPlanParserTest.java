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
