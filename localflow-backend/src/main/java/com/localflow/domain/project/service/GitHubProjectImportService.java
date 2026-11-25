package com.localflow.domain.project.service;

import com.localflow.domain.project.dto.request.GitHubProjectImportRequest;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.response.GitHubProjectImportResponse;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.workspace.config.WorkspaceProperties;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.service.ProjectFileService;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class GitHubProjectImportService {
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(2);

    private final ProjectService projectService;
    private final ProjectFileService fileService;
    private final WorkspaceStorageService storageService;
    private final WorkspaceProperties properties;

    public GitHubProjectImportService(ProjectService projectService, ProjectFileService fileService,
                                      WorkspaceStorageService storageService,
                                      WorkspaceProperties properties) {
        this.projectService = projectService;
        this.fileService = fileService;
        this.storageService = storageService;
        this.properties = properties;
    }

    public GitHubProjectImportResponse importPublicRepository(GitHubProjectImportRequest request) {
        GitHubRepositoryReference reference = GitHubRepositoryReference.parse(
                request.repositoryUrl(), request.branch());
        ProjectResponse project = projectService.create(new ProjectCreateRequest(
                request.name(), request.description()));
        Path sourceRoot = storageService.projectSourceRoot(project.id());
        try {
            cloneRepository(reference, sourceRoot);
            enforcePhysicalLimits(sourceRoot);
            deleteDirectory(sourceRoot.resolve(".git"), sourceRoot);
            FileSyncResponse files = fileService.indexExistingTree(
                    project.id(), reference.repository());
            return new GitHubProjectImportResponse(projectService.findById(project.id()), files,
                    reference.cloneUrl(), reference.branch());
        } catch (RuntimeException exception) {
            projectService.delete(project.id());
            throw exception;
        }
    }

    private void cloneRepository(GitHubRepositoryReference reference, Path sourceRoot) {
        try {
            Files.createDirectories(sourceRoot.getParent());
            List<String> command = new ArrayList<>(List.of(
                    "git", "clone", "--depth", "1", "--single-branch"));
            if (reference.branch() != null) {
                command.add("--branch");
                command.add(reference.branch());
            }
            command.add(reference.cloneUrl());
            command.add(sourceRoot.toString());
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean completed = process.waitFor(CLONE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
            }
            if (process.exitValue() != 0) throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.GITHUB_IMPORT_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        }
    }

    private void enforcePhysicalLimits(Path sourceRoot) {
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> allPaths = paths.toList();
            if (allPaths.stream().anyMatch(Files::isSymbolicLink)) {
                throw new CustomException(ErrorCode.INVALID_GITHUB_REPOSITORY);
            }
            List<Path> files = allPaths.stream().filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(sourceRoot.resolve(".git"))).toList();
            if (files.size() > properties.maxFiles()) throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
            long total = 0;
            for (Path file : files) {
                long size = Files.size(file);
                if (size > properties.maxFileBytes()) throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
                total += size;
                if (total > properties.maxProjectBytes()) throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
            }
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        }
    }

    private void deleteDirectory(Path target, Path sourceRoot) {
        Path normalized = target.toAbsolutePath().normalize();
        Path normalizedRoot = sourceRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)
                || !Files.exists(normalized)) return;
        try (var paths = Files.walk(normalized)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        }
    }
}
