package com.localflow.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.request.ProjectSettingsUpdateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.PrivacyMode;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.workspace.dto.FileContentResponse;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.domain.FileAnalysisStatus;
import com.localflow.domain.workspace.service.ProjectFileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProjectWorkspaceIntegrationTest {
    @Autowired private ProjectService projectService;
    @Autowired private ProjectFileService fileService;

    @Test
    void synchronizesDirectoryDetectsChangesAndIndexesSourceSymbols() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("student-api", "test"));
        MockMultipartFile javaFile = textFile("Main.java", """
                package sample;
                public class Main {
                    public void run() {
                    }
                }
                """);
        MockMultipartFile readme = textFile("README.md", "# Student API");
        MockMultipartFile ignored = textFile("package.js", "ignored");

        FileSyncResponse first = fileService.synchronize(project.id(),
                List.of(javaFile, readme, ignored),
                List.of("student-api/src/Main.java", "student-api/README.md",
                        "student-api/node_modules/package.js"), true, null);

        assertThat(first.added()).isEqualTo(2);
        assertThat(first.ignored()).isEqualTo(1);
        assertThat(first.totalFiles()).isEqualTo(2);

        String javaFileId = fileService.findAll(project.id()).stream()
                .filter(file -> file.fileName().equals("Main.java"))
                .findFirst().orElseThrow().id();
        FileContentResponse content = fileService.readContent(project.id(), javaFileId);
        assertThat(content.symbols()).extracting("name").contains("Main", "run");
        assertThat(content.file().tags()).contains("source-code", "java");
        var analysis = fileService.analysis(project.id(), javaFileId);
        assertThat(analysis.status()).isEqualTo(FileAnalysisStatus.ANALYZED);
        assertThat(analysis.role()).isEqualTo("소스 코드");
        assertThat(analysis.summary()).contains("코드 심볼 2개");
        assertThat(analysis.symbols()).extracting("name").contains("Main", "run");
        assertThat(analysis.analyzedAt()).isNotNull();

        MockMultipartFile changedJava = textFile("Main.java", """
                public class Main {
                    public void changed() {
                    }
                }
                """);
        FileSyncResponse second = fileService.synchronize(project.id(), List.of(changedJava),
                List.of("student-api/src/Main.java"), true, null);

        assertThat(second.modified()).isEqualTo(1);
        assertThat(second.deleted()).isEqualTo(1);
        assertThat(second.totalFiles()).isEqualTo(1);
        assertThat(fileService.readContent(project.id(), javaFileId).symbols())
                .extracting("name").contains("Main", "changed");
    }

    @Test
    void skipsUnchangedFileAndSupportsForcedReindex() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("incremental", "test"));
        MockMultipartFile source = textFile("Main.java", "public class Main {}\n");
        fileService.synchronize(project.id(), List.of(source),
                List.of("incremental/src/Main.java"), true, null);
        var before = fileService.findAll(project.id()).get(0);

        FileSyncResponse skipped = fileService.synchronize(project.id(), List.of(source),
                List.of("incremental/src/Main.java"), true, null, false);
        var after = fileService.findAll(project.id()).get(0);

        assertThat(skipped.unchanged()).isEqualTo(1);
        assertThat(skipped.changes().get(0).reason()).contains("재분석을 생략");
        assertThat(after.updatedAt()).isEqualTo(before.updatedAt());

        FileSyncResponse forced = fileService.synchronize(project.id(), List.of(source),
                List.of("incremental/src/Main.java"), true, null, true);
        assertThat(forced.unchanged()).isEqualTo(1);
        assertThat(forced.changes().get(0).reason()).contains("다시 분석");
    }

    @Test
    void synchronizesOnlySelectedDirectoriesWithoutDeletingFilesOutsideScope() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("scoped", "test"));
        fileService.synchronize(project.id(),
                List.of(textFile("A.java", "class A {}"), textFile("B.java", "class B {}")),
                List.of("scoped/src/a/A.java", "scoped/src/b/B.java"), true, null);

        FileSyncResponse result = fileService.synchronize(project.id(),
                List.of(textFile("A.java", "class A { void changed() {} }"),
                        textFile("B.java", "class B { void changed() {} }")),
                List.of("scoped/src/a/A.java", "scoped/src/b/B.java"), true, null, false,
                true, List.of("src/a"));

        assertThat(result.modified()).isEqualTo(1);
        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.deleted()).isZero();
        assertThat(result.scopePaths()).containsExactly("src/a");
        assertThat(fileService.findAll(project.id())).extracting("relativePath")
                .containsExactly("src/a/A.java", "src/b/B.java");
    }

    @Test
    void appliesProjectPathAndExtensionExclusionsBeforeStorageAndIndexing() {
        ProjectResponse project = projectService.create(new ProjectCreateRequest("excluded", "test"));
        projectService.updateSettings(project.id(), new ProjectSettingsUpdateRequest(
                ExecutionMode.BALANCED, PrivacyMode.LOCAL_ONLY,
                PermissionPolicy.ALLOW, PermissionPolicy.CONFIRM, PermissionPolicy.CONFIRM,
                PermissionPolicy.CONFIRM, PermissionPolicy.CONFIRM, PermissionPolicy.DENY,
                Set.of("generated/**"), Set.of(".log")));

        FileSyncResponse result = fileService.synchronize(project.id(),
                List.of(textFile("Main.java", "class Main {}"),
                        textFile("Generated.java", "class Generated {}"),
                        textFile("debug.log", "trace")),
                List.of("excluded/src/Main.java", "excluded/generated/Generated.java",
                        "excluded/logs/debug.log"), true, null);

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.ignored()).isEqualTo(2);
        assertThat(result.changes()).extracting("reason")
                .anyMatch(reason -> reason != null && reason.toString().contains("사용자 제외 경로"))
                .anyMatch(reason -> reason != null && reason.toString().contains("사용자 제외 확장자"));
        assertThat(fileService.findAll(project.id())).extracting("relativePath")
                .containsExactly("src/Main.java");
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
