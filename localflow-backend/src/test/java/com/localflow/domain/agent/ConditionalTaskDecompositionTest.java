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

    @Test
    void collapsesMultipleConditionalBranchTasksIntoSingleGuardedExecution() {
        String prompt = "JwtProvider.java가 있으면 최대 5문장으로 요약하고 없으면 후보 경로만 반환하라. 파일은 수정하지 마라.";
        var parsed = parser.parse("""
                {"summary":"조건부 JWT 조회","source":"AI","tasks":[
                  {"id":"check","instruction":"JwtProvider.java 존재 여부를 확인한다", "dependsOn":[],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]},
                  {"id":"present","instruction":"파일이 있으면 최대 5문장으로 요약한다", "dependsOn":["check"],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]},
                  {"id":"absent","instruction":"파일이 없으면 후보 경로만 반환한다", "dependsOn":["check"],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]}]}
                """);

        var guarded = parser.preserveConditionalBranches(prompt, parsed, List.of("jwt"));

        assertThat(guarded.source()).isEqualTo("CONDITION_GUARD");
        assertThat(guarded.tasks()).singleElement()
                .satisfies(task -> {
                    assertThat(task.instruction()).isEqualTo(prompt);
                    assertThat(task.dependsOn()).isEmpty();
                    assertThat(task.acceptanceCriteria())
                            .contains("원래 요청의 출력 형식과 파일 변경 제한을 그대로 지킨다.");
                });
    }

    @Test
    void restoresOriginalRequestWhenSingleAiTaskDropsGlobalRestrictions() {
        String prompt = "JwtProvider.java가 존재하면 최대 5문장으로 요약하고 존재하지 않으면 후보만 반환하라. 파일은 수정하지 마라.";
        var parsed = parser.parse("""
                {"summary":"조건부 JWT 조회","source":"AI","tasks":[
                  {"id":"check","instruction":"JwtProvider.java가 존재하면 최대 5문장으로 요약하고 존재하지 않으면 후보만 반환한다", "dependsOn":[],
                   "suggestedTags":["jwt"],"acceptanceCriteria":[]}]}
                """);

        var guarded = parser.preserveConditionalBranches(prompt, parsed, List.of("jwt"));

        assertThat(guarded.source()).isEqualTo("CONDITION_GUARD");
        assertThat(guarded.tasks()).singleElement()
                .satisfies(task -> assertThat(task.instruction()).isEqualTo(prompt));
    }
}
