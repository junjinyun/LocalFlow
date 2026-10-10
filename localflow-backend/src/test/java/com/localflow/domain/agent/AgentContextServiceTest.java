package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.service.AgentContextService;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextServiceTest {
    private final ProjectFileRepository fileRepository = mock(ProjectFileRepository.class);
    private final ProjectMemoryRepository memoryRepository = mock(ProjectMemoryRepository.class);
    private final ChatMessageRepository chatRepository = mock(ChatMessageRepository.class);
    private final WorkspaceStorageService storageService = mock(WorkspaceStorageService.class);
    private final AiProviderProperties properties = new AiProviderProperties(
            32_000, 20, null, null, null, null);
    private final AgentContextService service = new AgentContextService(
            fileRepository, memoryRepository, chatRepository, storageService, properties);

    @Test
    void reportsOnlyFilesWhoseContentsAreIncludedInTheGenerationContext() {
        Project project = Project.create("sample", "context snapshot test");
        ProjectFile source = ProjectFile.create(project, "src/UserService.java", "UserService.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash-source");
        ProjectFile document = ProjectFile.create(project, "docs/spec.docx", "spec.docx",
                "docx", FileCategory.DOCUMENT, null, 20, "hash-document");
        when(fileRepository.findAllByProject_IdOrderByRelativePath(project.getId()))
                .thenReturn(List.of(document, source));
        when(memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(chatRepository.findAllByProject_IdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(storageService.readText(project.getId(), source.getRelativePath()))
                .thenReturn("class UserService {}");
        DecisionResult decision = DecisionResult.fallback(
                "INSPECT_FILES", source.getRelativePath(), List.of(), "LOW", false);

        AgentContextService.ContextSnapshot snapshot = service.buildSnapshot(
                project, "UserService 설명", decision, true);

        assertThat(snapshot.files()).containsExactly(source.getRelativePath());
        assertThat(snapshot.content()).contains("## 파일: src/UserService.java", "class UserService {}");
    }

    @Test
    void reportsNoContextFilesWhenReadPermissionDeniesFileContents() {
        Project project = Project.create("sample", null);
        ProjectFile source = ProjectFile.create(project, "src/App.java", "App.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash");
        when(fileRepository.findAllByProject_IdOrderByRelativePath(project.getId()))
                .thenReturn(List.of(source));
        when(memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(chatRepository.findAllByProject_IdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        DecisionResult decision = DecisionResult.fallback(
                "INSPECT_FILES", source.getRelativePath(), List.of(), "LOW", false);

        AgentContextService.ContextSnapshot snapshot = service.buildSnapshot(
                project, "App 설명", decision, false);

        assertThat(snapshot.files()).isEmpty();
        assertThat(snapshot.content()).doesNotContain("## 파일: src/App.java");
    }
}
