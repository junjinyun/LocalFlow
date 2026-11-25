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
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GitHubProjectImportService {
    private final ProjectService projectService;
    private final ProjectFileService fileService;
    private final WorkspaceStorageService storageService;
    private final WorkspaceProperties properties;
    private final GitRepositoryCloneClient cloneClient;

    public GitHubProjectImportService(ProjectService projectService, ProjectFileService fileService,
                                      WorkspaceStorageService storageService,
                                      WorkspaceProperties properties,
                                      GitRepositoryCloneClient cloneClient) {
        this.projectService = projectService;
        this.fileService = fileService;
        this.storageService = storageService;
        this.properties = properties;
        this.cloneClient = cloneClient;
    }

    public GitHubProjectImportResponse importPublicRepository(GitHubProjectImportRequest request) {
        GitHubRepositoryReference reference = GitHubRepositoryReference.parse(
                request.repositoryUrl(), request.branch());
        ProjectResponse project = projectService.create(new ProjectCreateRequest(
                request.name(), request.description()));
        Path sourceRoot = storageService.projectSourceRoot(project.id());
        try {
            cloneClient.cloneShallow(reference, sourceRoot);
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
