package com.localflow.domain.workspace.service;

import com.localflow.domain.workspace.config.WorkspaceProperties;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class WorkspaceStorageService {
    private final WorkspaceProperties properties;
    private Path workspaceRoot;

    public WorkspaceStorageService(WorkspaceProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initialize() throws IOException {
        workspaceRoot = properties.root().toAbsolutePath().normalize();
        Files.createDirectories(workspaceRoot);
    }

    public Path projectSourceRoot(String projectId) {
        Path root = workspaceRoot.resolve(projectId).resolve("source").normalize();
        ensureInside(root, workspaceRoot);
        return root;
    }

    public Path resolveProjectFile(String projectId, String relativePath) {
        String normalizedValue = normalizeRelativePath(relativePath);
        Path projectRoot = projectSourceRoot(projectId);
        Path resolved = projectRoot.resolve(normalizedValue).normalize();
        ensureInside(resolved, projectRoot);
        return resolved;
    }

    public Path store(String projectId, String relativePath, MultipartFile multipartFile) {
        Path target = resolveProjectFile(projectId, relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = multipartFile.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("파일 저장에 실패했습니다: " + relativePath, exception);
        }
    }

    public Path writeText(String projectId, String relativePath, String content) {
        Path target = resolveProjectFile(projectId, relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("파일 저장에 실패했습니다: " + relativePath, exception);
        }
    }

    public String readText(String projectId, String relativePath) {
        Path source = resolveProjectFile(projectId, relativePath);
        if (!Files.isRegularFile(source)) {
            throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
        }
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("파일을 읽지 못했습니다: " + relativePath, exception);
        }
    }

    public byte[] readBytes(String projectId, String relativePath) {
        Path source = resolveProjectFile(projectId, relativePath);
        if (!Files.isRegularFile(source)) {
            throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
        }
        try {
            return Files.readAllBytes(source);
        } catch (IOException exception) {
            throw new IllegalStateException("파일을 읽지 못했습니다: " + relativePath, exception);
        }
    }

    public Path writeBytes(String projectId, String relativePath, byte[] content) {
        Path target = resolveProjectFile(projectId, relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content == null ? new byte[0] : content);
            return target;
        } catch (IOException exception) {
            throw new IllegalStateException("파일 복구에 실패했습니다: " + relativePath, exception);
        }
    }

    public Resource resource(String projectId, String relativePath) {
        Path source = resolveProjectFile(projectId, relativePath);
        if (!Files.isRegularFile(source)) {
            throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
        }
        return new PathResource(source);
    }

    public void move(String projectId, String sourcePath, String destinationPath) {
        Path source = resolveProjectFile(projectId, sourcePath);
        Path destination = resolveProjectFile(projectId, destinationPath);
        try {
            Files.createDirectories(destination.getParent());
            Files.move(source, destination);
            removeEmptyParents(source.getParent(), projectSourceRoot(projectId));
        } catch (IOException exception) {
            throw new IllegalStateException("파일 이동에 실패했습니다.", exception);
        }
    }

    public void delete(String projectId, String relativePath) {
        Path target = resolveProjectFile(projectId, relativePath);
        try {
            Files.deleteIfExists(target);
            removeEmptyParents(target.getParent(), projectSourceRoot(projectId));
        } catch (IOException exception) {
            throw new IllegalStateException("파일 삭제에 실패했습니다.", exception);
        }
    }

    public void deleteProject(String projectId) {
        Path target = workspaceRoot.resolve(projectId).normalize();
        ensureInside(target, workspaceRoot);
        if (!Files.exists(target)) {
            return;
        }
        try (var paths = Files.walk(target)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IOException exception) {
            throw new IllegalStateException("프로젝트 작업공간 삭제에 실패했습니다.", exception);
        }
    }

    public String normalizeRelativePath(String value) {
        if (value == null || value.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH);
        }
        String unixPath = value.strip().replace('\\', '/');
        Path normalized;
        try {
            normalized = Path.of(unixPath).normalize();
        } catch (InvalidPathException exception) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH);
        }
        String result = normalized.toString().replace('\\', '/');
        if (normalized.isAbsolute() || result.isBlank() || result.equals(".") || result.equals("..")
                || result.startsWith("../") || result.contains(":") || result.indexOf('\0') >= 0) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH);
        }
        return result;
    }

    private void ensureInside(Path target, Path parent) {
        if (!target.startsWith(parent)) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH);
        }
    }

    private void removeEmptyParents(Path current, Path stopAt) throws IOException {
        while (current != null && !current.equals(stopAt) && current.startsWith(stopAt)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }
}
