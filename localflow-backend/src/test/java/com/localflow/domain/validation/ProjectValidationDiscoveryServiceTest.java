package com.localflow.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandType;
import com.localflow.domain.validation.service.ProjectValidationDiscoveryService;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectValidationDiscoveryServiceTest {
    @Mock ProjectService projectService;
    @Mock ProjectFileRepository fileRepository;
    @Mock WorkspaceStorageService storageService;

    private ProjectValidationDiscoveryService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new ProjectValidationDiscoveryService(projectService, fileRepository,
                storageService, new ObjectMapper());
        project = Project.create("validation", null);
        when(projectService.requireProject(project.getId())).thenReturn(project);
    }

    @Test
    void discoversGradleAndKnownNpmScriptsWithoutUsingScriptContentAsCommand() {
        List<ProjectFile> files = List.of(
                file("server/build.gradle"), file("server/gradlew.bat"),
                file("client/package.json"));
        when(fileRepository.findAllByProject_IdOrderByRelativePath(project.getId())).thenReturn(files);
        when(storageService.readText(project.getId(), "client/package.json")).thenReturn("""
                {"scripts":{"build":"dangerous-command", "typecheck":"tsc --noEmit", "deploy":"unknown"}}
                """);

        var result = service.discover(project.getId());

        assertThat(result).extracting(value -> value.tool()).contains(ValidationBuildTool.GRADLE,
                ValidationBuildTool.NPM);
        assertThat(result).extracting(value -> value.type()).contains(
                ValidationCommandType.COMPILE, ValidationCommandType.TEST,
                ValidationCommandType.BUILD, ValidationCommandType.TYPECHECK);
        assertThat(result).extracting(value -> value.displayCommand())
                .contains("gradlew.bat compileJava --no-daemon", "npm run build")
                .doesNotContain("dangerous-command");
        assertThat(result).noneMatch(value -> value.displayCommand().contains("deploy"));
    }

    private ProjectFile file(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : null;
        return ProjectFile.create(project, path, name, extension,
                FileCategory.SOURCE_CODE, null, 10, "a".repeat(64));
    }
}
