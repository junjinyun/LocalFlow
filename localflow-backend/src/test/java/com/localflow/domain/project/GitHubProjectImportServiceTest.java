package com.localflow.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.localflow.domain.project.dto.request.GitHubProjectImportRequest;
import com.localflow.domain.project.dto.response.GitHubProjectImportResponse;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.entity.ProjectStatus;
import com.localflow.domain.project.service.GitHubProjectImportService;
import com.localflow.domain.project.service.GitRepositoryCloneClient;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.workspace.config.WorkspaceProperties;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubProjectImportServiceTest {
    @TempDir Path tempDir;

    @Test
    void clonesIndexesAndReturnsImportedProject() throws Exception {
        ProjectService projectService = mock(ProjectService.class);
        ProjectFileService fileService = mock(ProjectFileService.class);
        WorkspaceStorageService storageService = mock(WorkspaceStorageService.class);
        GitRepositoryCloneClient cloneClient = mock(GitRepositoryCloneClient.class);
        WorkspaceProperties properties = new WorkspaceProperties(tempDir,
                1_000_000, 100_000, 100, List.of(".git", "node_modules"));
        ProjectResponse created = new ProjectResponse("project-id", "LocalFlow", null,
                null, ProjectStatus.CREATED, Instant.now(), Instant.now());
        ProjectResponse ready = new ProjectResponse("project-id", "LocalFlow", null,
                "LocalFlow", ProjectStatus.READY, created.createdAt(), Instant.now());
        FileSyncResponse indexed = new FileSyncResponse(1, 0, 0, 0, 0,
                1, false, List.of(), List.of());
        Path sourceRoot = tempDir.resolve("project-id/source");
        when(projectService.create(any())).thenReturn(created);
        when(projectService.findById("project-id")).thenReturn(ready);
        when(storageService.projectSourceRoot("project-id")).thenReturn(sourceRoot);
        when(fileService.indexExistingTree("project-id", "LocalFlow")).thenReturn(indexed);
        doAnswer(invocation -> {
            Files.createDirectories(sourceRoot.resolve(".git"));
            Files.writeString(sourceRoot.resolve(".git/config"), "config");
            Files.createDirectories(sourceRoot.resolve("src"));
            Files.writeString(sourceRoot.resolve("src/App.ts"), "export default {};");
            return null;
        }).when(cloneClient).cloneShallow(any(), any());
        GitHubProjectImportService service = new GitHubProjectImportService(
                projectService, fileService, storageService, properties, cloneClient);

        GitHubProjectImportResponse result = service.importPublicRepository(
                new GitHubProjectImportRequest("LocalFlow", null,
                        "https://github.com/junjinyun/LocalFlow", "dev"));

        assertThat(result.project()).isEqualTo(ready);
        assertThat(result.files()).isEqualTo(indexed);
        assertThat(result.branch()).isEqualTo("dev");
        assertThat(sourceRoot.resolve(".git")).doesNotExist();
        verify(fileService).indexExistingTree("project-id", "LocalFlow");
    }
}
