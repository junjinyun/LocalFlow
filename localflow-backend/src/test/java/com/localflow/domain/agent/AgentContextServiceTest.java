package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.service.AgentContextService;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.memory.service.ProjectMemoryScopeMatcher;
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
            fileRepository, memoryRepository, chatRepository, storageService, properties,
            new ProjectMemoryScopeMatcher());

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
        assertThat(snapshot.usedChars()).isLessThanOrEqualTo(snapshot.maxChars());
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

    @Test
    void reportsTruncatedAndExcludedFilesWhenBudgetIsLimited() {
        Project project = Project.create("sample", null);
        ProjectFile primary = ProjectFile.create(project, "src/App.java", "App.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash-1");
        ProjectFile secondary = ProjectFile.create(project, "src/Other.java", "Other.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash-2");
        when(fileRepository.findAllByProject_IdOrderByRelativePath(project.getId()))
                .thenReturn(List.of(primary, secondary));
        when(memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(chatRepository.findAllByProject_IdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(storageService.readText(project.getId(), primary.getRelativePath()))
                .thenReturn("x".repeat(10_000));
        when(storageService.readText(project.getId(), secondary.getRelativePath()))
                .thenReturn("class Other {}");
        DecisionResult decision = DecisionResult.fallback(
                "UPDATE_FILES", primary.getRelativePath(), List.of(), "LOW", false);

        AgentContextService.ContextSnapshot snapshot = service.buildSnapshot(
                project, "App 수정", decision, true, 4_000);

        assertThat(snapshot.usedChars()).isLessThanOrEqualTo(4_000);
        assertThat(snapshot.truncatedFiles()).contains(primary.getRelativePath());
    }

    @Test
    void ranksFilesDeterministicallyAndRecordsSelectionReasons() {
        Project project = Project.create("sample", null);
        ProjectFile tagged = ProjectFile.create(project, "src/auth/JwtService.java", "JwtService.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash-tagged");
        tagged.replaceTags(List.of("authentication", "jwt"));
        ProjectFile other = ProjectFile.create(project, "src/user/UserService.java", "UserService.java",
                "java", FileCategory.SOURCE_CODE, "java", 20, "hash-other");
        when(fileRepository.findAllByProject_IdOrderByRelativePath(project.getId()))
                .thenReturn(List.of(other, tagged));
        when(memoryRepository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(project.getId()))
                .thenReturn(List.of());
        when(chatRepository.findAllByProject_IdOrderByCreatedAtAsc(project.getId()))
                .thenReturn(List.of());
        when(storageService.readText(project.getId(), tagged.getRelativePath())).thenReturn("jwt");
        when(storageService.readText(project.getId(), other.getRelativePath())).thenReturn("user");
        DecisionResult decision = DecisionResult.fallback(
                "INSPECT_FILES", null,
                List.of(new com.localflow.domain.provider.domain.TaskScope(
                        "JWT 인증 확인", List.of("authentication"))), "LOW", false);

        var first = service.buildSnapshot(project, "JWT 인증 확인", decision, true);
        var second = service.buildSnapshot(project, "JWT 인증 확인", decision, true);

        assertThat(first.fileSelections()).isEqualTo(second.fileSelections());
        assertThat(first.fileSelections().get(0).path()).isEqualTo(tagged.getRelativePath());
        assertThat(first.fileSelections().get(0).score()).isGreaterThan(first.fileSelections().get(1).score());
        assertThat(first.fileSelections().get(0).reasons())
                .anyMatch(reason -> reason.contains("선택 태그 일치"));
    }
}
