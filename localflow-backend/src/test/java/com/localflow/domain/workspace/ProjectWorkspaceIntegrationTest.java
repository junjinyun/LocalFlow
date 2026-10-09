package com.localflow.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.workspace.dto.FileContentResponse;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.service.ProjectFileService;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
