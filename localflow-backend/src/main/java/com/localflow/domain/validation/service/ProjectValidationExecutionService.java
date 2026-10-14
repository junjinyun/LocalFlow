package com.localflow.domain.validation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.domain.validation.config.ValidationProperties;
import com.localflow.domain.validation.domain.ValidationBuildTool;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.dto.ValidationCommandResult;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ProjectValidationExecutionService {
    private static final int MAX_STORED_RESULTS = 20;

    private final ValidationProperties properties;
    private final ProjectValidationDiscoveryService discoveryService;
    private final BoundedProcessExecutor processExecutor;
    private final WorkspaceStorageService storageService;
    private final AgentRunService runService;
    private final AgentRunRepository runRepository;
    private final AgentRunProgressService progressService;
    private final ObjectMapper objectMapper;

    public ProjectValidationExecutionService(ValidationProperties properties,
                                             ProjectValidationDiscoveryService discoveryService,
                                             BoundedProcessExecutor processExecutor,
                                             WorkspaceStorageService storageService,
                                             AgentRunService runService,
                                             AgentRunRepository runRepository,
                                             AgentRunProgressService progressService,
                                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.discoveryService = discoveryService;
        this.processExecutor = processExecutor;
        this.storageService = storageService;
        this.runService = runService;
        this.runRepository = runRepository;
        this.progressService = progressService;
        this.objectMapper = objectMapper;
    }

    public ValidationCommandResult execute(String projectId, String runId, String commandId) {
        if (!properties.enabled()) throw new CustomException(ErrorCode.VALIDATION_EXECUTION_DISABLED);
        AgentRun run = runService.requireRun(projectId, runId);
        if (run.getStatus() != AgentRunStatus.COMPLETED) {
            throw new CustomException(ErrorCode.INVALID_RUN_STATUS);
        }
        ValidationCommandSpec spec = discoveryService.discover(projectId).stream()
                .filter(value -> value.id().equals(commandId)).findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_VALIDATION_COMMAND));
        Path workingDirectory = resolveWorkingDirectory(projectId, spec.workingDirectory());
        List<String> command = command(spec, workingDirectory);
        progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                spec.displayName() + " 검증을 실행하는 중입니다.");
        Instant startedAt = Instant.now();
        ValidationCommandResult.ProcessExecutionOutput output;
        try {
            output = processExecutor.execute(command, workingDirectory);
        } catch (RuntimeException exception) {
            output = new ValidationCommandResult.ProcessExecutionOutput(null, false,
                    exception.getMessage() == null ? "검증 실행에 실패했습니다." : exception.getMessage(), false);
        }
        ValidationCommandResult result = ValidationCommandResult.from(
                spec, output, startedAt, Instant.now());
        store(run, result);
        progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                spec.displayName() + " 검증 결과: " + result.status().name());
        return result;
    }

    private Path resolveWorkingDirectory(String projectId, String directory) {
        Path root = storageService.projectSourceRoot(projectId).toAbsolutePath().normalize();
        Path resolved = directory.equals(".") ? root : root.resolve(directory).normalize();
        if (!resolved.startsWith(root) || !Files.isDirectory(resolved)) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH);
        }
        return resolved;
    }

    private List<String> command(ValidationCommandSpec spec, Path workingDirectory) {
        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String executable;
        if (spec.tool() == ValidationBuildTool.GRADLE && spec.executable().startsWith("gradlew")) {
            Path wrapper = workingDirectory.resolve(spec.executable()).normalize();
            if (!Files.isRegularFile(wrapper)) throw new CustomException(ErrorCode.INVALID_VALIDATION_COMMAND);
            executable = wrapper.toString();
        } else if (spec.tool() == ValidationBuildTool.NPM) {
            executable = windows ? "npm.cmd" : "npm";
        } else {
            executable = "gradle";
        }
        if (windows && executable.toLowerCase(Locale.ROOT).endsWith(".bat")) {
            command.add(System.getenv().getOrDefault("ComSpec", "cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add('"' + executable + '"' + " " + String.join(" ", spec.arguments()));
        } else {
            command.add(executable);
            command.addAll(spec.arguments());
        }
        return List.copyOf(command);
    }

    private void store(AgentRun run, ValidationCommandResult result) {
        try {
            List<ValidationCommandResult> results = new ArrayList<>();
            if (run.getValidationResultsJson() != null && !run.getValidationResultsJson().isBlank()) {
                results.addAll(objectMapper.readValue(run.getValidationResultsJson(),
                        new TypeReference<List<ValidationCommandResult>>() { }));
            }
            results.add(result);
            if (results.size() > MAX_STORED_RESULTS) {
                results = new ArrayList<>(results.subList(results.size() - MAX_STORED_RESULTS, results.size()));
            }
            run.recordValidationResults(objectMapper.writeValueAsString(results));
            runRepository.saveAndFlush(run);
        } catch (Exception exception) {
            throw new IllegalStateException("검증 결과를 저장하지 못했습니다.", exception);
        }
    }
}
