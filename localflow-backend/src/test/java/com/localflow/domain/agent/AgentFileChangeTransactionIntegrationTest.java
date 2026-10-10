package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.agent.service.AgentFileApplyException;
import com.localflow.domain.agent.service.AgentFileChangeTransactionService;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.workspace.dto.ProjectFileResponse;
import com.localflow.domain.workspace.service.ProjectFileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "localflow.workspace.max-file-bytes=1024")
class AgentFileChangeTransactionIntegrationTest {
    @Autowired private ProjectService projectService;
    @Autowired private ProjectFileService fileService;
    @Autowired private AgentFileChangeTransactionService transactionService;

    @Test
    void rollsBackCreateUpdateMoveAndDeleteWhenLaterOperationFails() {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("atomic-rollback", "원자적 파일 적용 테스트"));
        try {
            fileService.synchronize(project.id(),
                    List.of(textFile("a.txt", "original-a"), textFile("b.txt", "original-b"),
                            textFile("d.txt", "original-d")),
                    List.of("atomic-rollback/a.txt", "atomic-rollback/b.txt",
                            "atomic-rollback/d.txt"), true, null);
            AgentPlan plan = new AgentPlan("rollback", "rollback", List.of(
                    new FileOperationPlan(FileOperationAction.UPDATE, "a.txt", null, "changed-a"),
                    new FileOperationPlan(FileOperationAction.CREATE, "c.txt", null, "created-c"),
                    new FileOperationPlan(FileOperationAction.MOVE, "b.txt", "moved/b.txt", null),
                    new FileOperationPlan(FileOperationAction.DELETE, "d.txt", null, null),
                    new FileOperationPlan(FileOperationAction.CREATE, "too-big.txt", null,
                            "x".repeat(1025))
            ));

            assertThatThrownBy(() -> transactionService.apply(project.id(), plan, () -> { }))
                    .isInstanceOfSatisfying(AgentFileApplyException.class, exception -> {
                        assertThat(exception.result().rollbackAttempted()).isTrue();
                        assertThat(exception.result().rollbackSuccessful()).isTrue();
                        assertThat(exception.result().appliedOperations()).isEqualTo(4);
                    });

            assertThat(paths(project.id())).containsExactly("a.txt", "b.txt", "d.txt");
            assertThat(content(project.id(), "a.txt")).isEqualTo("original-a");
            assertThat(content(project.id(), "b.txt")).isEqualTo("original-b");
            assertThat(content(project.id(), "d.txt")).isEqualTo("original-d");
        } finally {
            projectService.delete(project.id());
        }
    }

    @Test
    void rejectsInvalidPlanBeforeChangingAnyFile() {
        ProjectResponse project = projectService.create(
                new ProjectCreateRequest("atomic-prevalidation", "사전 검증 테스트"));
        try {
            fileService.synchronize(project.id(), List.of(textFile("a.txt", "original")),
                    List.of("atomic-prevalidation/a.txt"), true, null);
            AgentPlan plan = new AgentPlan("invalid", "invalid", List.of(
                    new FileOperationPlan(FileOperationAction.UPDATE, "a.txt", null, "changed"),
                    new FileOperationPlan(FileOperationAction.DELETE, "missing.txt", null, null)
            ));

            assertThatThrownBy(() -> transactionService.apply(project.id(), plan, () -> { }))
                    .isInstanceOf(RuntimeException.class);

            assertThat(paths(project.id())).containsExactly("a.txt");
            assertThat(content(project.id(), "a.txt")).isEqualTo("original");
        } finally {
            projectService.delete(project.id());
        }
    }

    private List<String> paths(String projectId) {
        return fileService.findAll(projectId).stream().map(ProjectFileResponse::relativePath).toList();
    }

    private String content(String projectId, String path) {
        ProjectFileResponse file = fileService.findAll(projectId).stream()
                .filter(candidate -> candidate.relativePath().equals(path)).findFirst().orElseThrow();
        return fileService.readContent(projectId, file.id()).content();
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
