package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.service.PromptTaskDecomposer;
import com.localflow.domain.agent.service.TaskDecompositionParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConditionalTaskDecompositionTest {
    private final TaskDecompositionParser parser =
            new TaskDecompositionParser(new ObjectMapper(), new PromptTaskDecomposer());

    @Test
    void restoresOriginalConditionalRequestWhenAiDropsFallbackBranch() {
        String prompt = "JWT 파일이 있으면 목록을 반환하고 없으면 최소 JWT 코드를 작성해줘";
        var parsed = parser.parse("""
                {"summary":"JWT 조회","source":"AI","tasks":[
                  {"id":"task-1","instruction":"JWT 파일을 찾아 목록을 반환한다", "dependsOn":[],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]}]}
                """);

        var guarded = parser.preserveConditionalBranches(prompt, parsed, List.of("jwt"));

        assertThat(guarded.source()).isEqualTo("CONDITION_GUARD");
        assertThat(guarded.tasks()).hasSize(1);
        assertThat(guarded.tasks().get(0).instruction()).isEqualTo(prompt);
        assertThat(guarded.tasks().get(0).acceptanceCriteria()).isNotEmpty();
    }

    @Test
    void keepsAiDecompositionWhenBothConditionalBranchesRemain() {
        String prompt = "JWT가 있으면 목록화하고 없으면 생성한다";
        var parsed = parser.parse("""
                {"summary":"조건부 JWT","source":"AI","tasks":[
                  {"id":"task-1","instruction":"JWT가 있으면 목록화하고 없으면 생성한다", "dependsOn":[],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]}]}
                """);

        assertThat(parser.preserveConditionalBranches(prompt, parsed, List.of("jwt")))
                .isEqualTo(parsed);
    }
}
