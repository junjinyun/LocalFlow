package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.service.PromptTaskDecomposer;
import com.localflow.domain.agent.service.TaskDecompositionParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskDecompositionParserTest {
    private final TaskDecompositionParser parser = new TaskDecompositionParser(
            new ObjectMapper(), new PromptTaskDecomposer());

    @Test
    void parsesAiTasksWithDependenciesAndAcceptanceCriteria() {
        var result = parser.parse("""
                {"summary":"인증 구현","tasks":[
                  {"id":"task-1","instruction":"로컬 로그인 구현","dependsOn":[],
                   "suggestedTags":["login","authentication"],"acceptanceCriteria":["로그인 API 제공"]},
                  {"id":"task-2","instruction":"JWT 연동","dependsOn":["task-1"],
                   "suggestedTags":["jwt","security"],"acceptanceCriteria":["Bearer 토큰 검증"]}
                ]}
                """);

        assertThat(result.source()).isEqualTo("AI");
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(1).dependsOn()).containsExactly("task-1");
        assertThat(result.tasks().get(1).acceptanceCriteria()).contains("Bearer 토큰 검증");
    }

    @Test
    void createsLocalFallbackWhenAiDecompositionCannotBeUsed() {
        var result = parser.fallback("로컬 로그인 추가 후 JWT 연동", List.of("login", "jwt"));

        assertThat(result.source()).isEqualTo("LOCAL_FALLBACK");
        assertThat(result.tasks()).extracting("instruction")
                .containsExactly("로컬 로그인 추가", "JWT 연동");
        assertThat(result.tasks().get(1).dependsOn()).containsExactly("task-1");
    }
}
