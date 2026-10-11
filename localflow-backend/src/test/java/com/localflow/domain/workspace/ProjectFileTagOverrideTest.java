package com.localflow.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.project.entity.Project;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectFileTagOverrideTest {
    @Test
    void combinesAutomaticAndUserTagsAndKeepsOverridesAfterReindex() {
        ProjectFile file = ProjectFile.create(Project.create("tags", null),
                "src/AuthController.java", "AuthController.java", "java",
                FileCategory.SOURCE_CODE, "Java", 10, "hash");
        file.replaceTags(Set.of("java", "controller", "api"));
        file.updateTagOverrides(Set.of("login", "security"), Set.of("api"));

        assertThat(file.getTags()).containsExactlyInAnyOrder(
                "java", "controller", "login", "security");

        file.replaceTags(Set.of("java", "controller", "api", "backend"));

        assertThat(file.getTags()).containsExactlyInAnyOrder(
                "java", "controller", "backend", "login", "security");
        assertThat(file.getUserExcludedTags()).containsExactly("api");
    }
}
