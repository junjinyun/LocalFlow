package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.service.PromptTaskDecomposer;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptTaskDecomposerTest {
    private final PromptTaskDecomposer decomposer = new PromptTaskDecomposer();

    @Test
    void splitsSequentialRequestAndAssignsTagsPerTask() {
        var scopes = decomposer.scopes("로컬 로그인 추가 후 JWT 연동해 달라",
                List.of("authentication", "login", "jwt", "security", "user"));

        assertThat(scopes).extracting("instruction")
                .containsExactly("로컬 로그인 추가", "JWT 연동해 달라");
        assertThat(scopes.get(0).tags()).contains("authentication", "login");
        assertThat(scopes.get(1).tags()).contains("authentication", "jwt");
    }
}
