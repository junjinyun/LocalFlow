package com.localflow.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.validation.config.ValidationProperties;
import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.domain.ValidationCommandType;
import com.localflow.domain.validation.domain.ValidationExecutionStatus;
import com.localflow.domain.validation.dto.ValidationCommandResult.ProcessExecutionOutput;
import com.localflow.domain.validation.service.BoundedProcessExecutor;
import com.localflow.domain.validation.service.ProjectValidationDiscoveryService;
import com.localflow.domain.validation.service.ProjectValidationExecutionService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectValidationExecutionServiceTest {
    @Mock ProjectValidationDiscoveryService discoveryService;
    @Mock BoundedProcessExecutor processExecutor;
    @Mock WorkspaceStorageService storageService;
    @Mock AgentRunService runService;
    @Mock AgentRunRepository runRepository;
    @Mock AgentRunProgressService progressService;

    @TempDir Path workspace;

    @Test
    void executesOnlyRediscoveredCommandAndStoresResult() throws Exception {
        Project project = Project.create("validation", null);
        AgentRun run = AgentRun.draft(project, "test", ExecutionMode.BALANCED, null, null);
        run.complete(null, null, "done", null, null, null);
        ValidationCommandSpec spec = new ValidationCommandSpec("gradle-test-id",
                ValidationBuildTool.GRADLE, ValidationCommandType.TEST, ".", "build.gradle",
                "Gradle 테스트", "gradle test --no-daemon", "gradle",
                List.of("test", "--no-daemon"));
        Files.createDirectories(workspace);
        when(runService.requireRun(project.getId(), run.getId())).thenReturn(run);
        when(discoveryService.discover(project.getId())).thenReturn(List.of(spec));
        when(storageService.projectSourceRoot(project.getId())).thenReturn(workspace);
        when(processExecutor.execute(any(), any())).thenReturn(
                new ProcessExecutionOutput(0, false, "BUILD SUCCESSFUL", false));
        ProjectValidationExecutionService service = new ProjectValidationExecutionService(
                new ValidationProperties(true, Duration.ofSeconds(5), 1000, 1),
                discoveryService, processExecutor, storageService, runService, runRepository,
                progressService, new ObjectMapper().findAndRegisterModules());

        var result = service.execute(project.getId(), run.getId(), spec.id());

        assertThat(result.status()).isEqualTo(ValidationExecutionStatus.SUCCESS);
        assertThat(run.getValidationResultsJson()).contains("BUILD SUCCESSFUL");
        verify(processExecutor).execute(any(), any());
        verify(runRepository).saveAndFlush(run);
    }
}
