package com.localflow.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.memory.domain.MemoryType;
import com.localflow.domain.memory.entity.ProjectMemory;
import com.localflow.domain.memory.service.ProjectMemoryScopeMatcher;
import com.localflow.domain.project.entity.Project;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectMemoryScopeMatcherTest {
    private final ProjectMemoryScopeMatcher matcher = new ProjectMemoryScopeMatcher();

    @Test
    void requiresEveryConfiguredScopeDimensionToMatch() {
        ProjectMemory memory = ProjectMemory.create(Project.create("scope", null),
                MemoryType.PROJECT_RULE, "인증 규칙", "JWT 사용", false,
                Set.of("authentication"), Set.of("src/**/auth/*"), Set.of("modify_files"));

        assertThat(matcher.matches(memory, "MODIFY_FILES", Set.of("authentication"),
                List.of("src/main/auth/JwtService.java"))).isTrue();
        assertThat(matcher.matches(memory, "MODIFY_FILES", Set.of("user"),
                List.of("src/main/auth/JwtService.java"))).isFalse();
        assertThat(matcher.matches(memory, "CREATE_FILES", Set.of("authentication"),
                List.of("src/main/auth/JwtService.java"))).isFalse();
    }

    @Test
    void globalMemoryAlwaysMatchesWhenActive() {
        ProjectMemory memory = ProjectMemory.create(Project.create("scope", null),
                MemoryType.NOTE, "전역", "항상 적용");

        assertThat(matcher.matches(memory, "ANSWER_ONLY", Set.of(), List.of())).isTrue();
    }
}
