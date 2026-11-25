package com.localflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.project.service.GitHubRepositoryReference;
import com.localflow.global.error.CustomException;
import org.junit.jupiter.api.Test;

class GitHubRepositoryReferenceTest {
    @Test
    void parsesPublicHttpsRepositoryAndOptionalBranch() {
        GitHubRepositoryReference reference = GitHubRepositoryReference.parse(
                "https://github.com/junjinyun/LocalFlow.git", "dev");

        assertThat(reference.owner()).isEqualTo("junjinyun");
        assertThat(reference.repository()).isEqualTo("LocalFlow");
        assertThat(reference.branch()).isEqualTo("dev");
        assertThat(reference.cloneUrl()).isEqualTo(
                "https://github.com/junjinyun/LocalFlow.git");
    }

    @Test
    void rejectsNonGithubHostsAndUnsafeBranches() {
        assertThatThrownBy(() -> GitHubRepositoryReference.parse(
                "https://example.com/owner/repo", null)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> GitHubRepositoryReference.parse(
                "https://github.com/owner/repo", "--upload-pack=bad"))
                .isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> GitHubRepositoryReference.parse(
                "git@github.com:owner/repo.git", null)).isInstanceOf(CustomException.class);
    }
}
