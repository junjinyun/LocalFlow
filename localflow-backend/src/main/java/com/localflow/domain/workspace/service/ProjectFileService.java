package com.localflow.domain.workspace.service;

import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.entity.ProjectSettings;
import com.localflow.domain.project.repository.ProjectSettingsRepository;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.domain.workspace.config.WorkspaceProperties;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.domain.FileChangeType;
import com.localflow.domain.workspace.dto.CodeSymbolResponse;
import com.localflow.domain.workspace.dto.FileContentResponse;
import com.localflow.domain.workspace.dto.FileMoveRequest;
import com.localflow.domain.workspace.dto.FileSyncItemResponse;
import com.localflow.domain.workspace.dto.FileSyncResponse;
import com.localflow.domain.workspace.dto.FileWriteRequest;
import com.localflow.domain.workspace.dto.ProjectFileResponse;
import com.localflow.domain.workspace.entity.ProjectFile;
import com.localflow.domain.workspace.repository.CodeSymbolRepository;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class ProjectFileService {
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "py", "js", "jsx", "ts", "tsx", "c", "h", "cpp", "hpp",
            "cs", "go", "rs", "php", "rb", "swift", "scala", "vue", "svelte", "html", "css",
            "scss", "sql", "sh", "ps1");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "md", "adoc", "csv", "tsv", "log");
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            "json", "yml", "yaml", "xml", "toml", "ini", "conf", "properties", "gradle");
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("doc", "docx", "hwp", "hwpx", "rtf");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            ".env", ".env.local", ".env.production", "id_rsa", "id_ed25519");

    private final ProjectFileRepository fileRepository;
    private final CodeSymbolRepository symbolRepository;
    private final ProjectSettingsRepository settingsRepository;
    private final ProjectService projectService;
    private final WorkspaceStorageService storageService;
    private final SourceIndexService sourceIndexService;
    private final WorkspaceProperties properties;

    public ProjectFileService(ProjectFileRepository fileRepository,
                              CodeSymbolRepository symbolRepository,
                              ProjectSettingsRepository settingsRepository,
                              ProjectService projectService,
                              WorkspaceStorageService storageService,
                              SourceIndexService sourceIndexService,
                              WorkspaceProperties properties) {
        this.fileRepository = fileRepository;
        this.symbolRepository = symbolRepository;
        this.settingsRepository = settingsRepository;
        this.projectService = projectService;
        this.storageService = storageService;
        this.sourceIndexService = sourceIndexService;
        this.properties = properties;
    }

    @Transactional
    public FileSyncResponse synchronize(String projectId, List<MultipartFile> files,
                                        List<String> relativePaths, boolean fullSync, String scope) {
        Project project = projectService.requireProject(projectId);
        validateBatch(files, relativePaths);

        List<String> normalizedOriginal = relativePaths.stream()
                .map(storageService::normalizeRelativePath).toList();
        String rootDirectory = commonRoot(normalizedOriginal);
        List<String> normalizedPaths = normalizedOriginal.stream()
                .map(path -> stripRoot(path, rootDirectory)).toList();
        String normalizedScope = normalizeScope(scope, rootDirectory);

        Map<String, ProjectFile> existing = new HashMap<>();
        fileRepository.findAllByProject_IdOrderByRelativePath(projectId)
                .forEach(file -> existing.put(file.getRelativePath(), file));
        Set<String> acceptedPaths = validateProjectedLimits(files, normalizedPaths, existing,
                fullSync, normalizedScope);
        List<FileSyncItemResponse> changes = new ArrayList<>();

        for (int index = 0; index < files.size(); index++) {
            MultipartFile upload = files.get(index);
            String relativePath = normalizedPaths.get(index);
            String ignoreReason = ignoreReason(relativePath);
            if (ignoreReason != null) {
                changes.add(new FileSyncItemResponse(relativePath, FileChangeType.IGNORED, ignoreReason));
                continue;
            }
            Path stored = storageService.store(projectId, relativePath, upload);
            String sha256 = hash(stored);
            ProjectFile current = existing.get(relativePath);
            FileChangeType changeType = current == null ? FileChangeType.ADDED
                    : current.getSha256().equals(sha256) ? FileChangeType.UNCHANGED : FileChangeType.MODIFIED;
            ProjectFile saved = saveMetadata(project, current, relativePath, upload.getSize(), sha256);
            if (changeType != FileChangeType.UNCHANGED || saved.getTags().isEmpty()) {
                sourceIndexService.reindex(saved, stored);
            }
            changes.add(new FileSyncItemResponse(relativePath, changeType, null));
        }

        if (fullSync) {
            for (ProjectFile stale : existing.values()) {
                if (!acceptedPaths.contains(stale.getRelativePath()) && inScope(stale.getRelativePath(), normalizedScope)) {
                    symbolRepository.deleteAllByProjectFile_Id(stale.getId());
                    fileRepository.delete(stale);
                    storageService.delete(projectId, stale.getRelativePath());
                    changes.add(new FileSyncItemResponse(stale.getRelativePath(), FileChangeType.DELETED, null));
                }
            }
        }

        project.attachDirectory(rootDirectory == null ? "uploaded-files" : rootDirectory);
        fileRepository.flush();
        return summarize(projectId, changes);
    }

    public List<ProjectFileResponse> findAll(String projectId) {
        projectService.requireProject(projectId);
        return fileRepository.findAllByProject_IdOrderByRelativePath(projectId).stream()
                .map(ProjectFileResponse::from).toList();
    }

    public FileContentResponse readContent(String projectId, String fileId) {
        ProjectFile file = requireFile(projectId, fileId);
        if (file.getCategory() == FileCategory.DOCUMENT) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE);
        }
        String content = storageService.readText(projectId, file.getRelativePath());
        List<CodeSymbolResponse> symbols = symbolRepository
                .findAllByProjectFile_IdOrderByLineNumber(file.getId()).stream()
                .map(CodeSymbolResponse::from).toList();
        return new FileContentResponse(ProjectFileResponse.from(file), content, symbols);
    }

    public Resource resource(String projectId, String fileId) {
        ProjectFile file = requireFile(projectId, fileId);
        return storageService.resource(projectId, file.getRelativePath());
    }

    public ProjectFile requireFile(String projectId, String fileId) {
        ProjectFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND));
        if (!file.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.PROJECT_FILE_NOT_FOUND);
        }
        return file;
    }

    @Transactional
    public ProjectFileResponse write(String projectId, String fileId, FileWriteRequest request) {
        Project project = projectService.requireProject(projectId);
        String relativePath = storageService.normalizeRelativePath(request.relativePath());
        String ignoreReason = ignoreReason(relativePath);
        if (ignoreReason != null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_FILE);
        }
        ProjectFile current = fileId == null ? fileRepository
                .findByProject_IdAndRelativePath(projectId, relativePath).orElse(null)
                : requireFile(projectId, fileId);
        PermissionPolicy policy = current == null ? settings(projectId).getCreatePolicy()
                : settings(projectId).getEditPolicy();
        requirePermission(policy, request.approved());
        if (current != null && !current.getRelativePath().equals(relativePath)) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        long requestedSize = (request.content() == null ? "" : request.content())
                .getBytes(StandardCharsets.UTF_8).length;
        List<ProjectFile> existingFiles = fileRepository.findAllByProject_IdOrderByRelativePath(projectId);
        long projectedBytes = existingFiles.stream().mapToLong(ProjectFile::getSizeBytes).sum()
                - (current == null ? 0 : current.getSizeBytes()) + requestedSize;
        long projectedCount = existingFiles.size() + (current == null ? 1 : 0);
        if (requestedSize > properties.maxFileBytes() || projectedBytes > properties.maxProjectBytes()
                || projectedCount > properties.maxFiles()) {
            throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
        }
        Path stored = storageService.writeText(projectId, relativePath, request.content());
        long size = fileSize(stored);
        if (size > properties.maxFileBytes()) {
            storageService.delete(projectId, relativePath);
            throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
        }
        ProjectFile saved = saveMetadata(project, current, relativePath, size, hash(stored));
        sourceIndexService.reindex(saved, stored);
        return ProjectFileResponse.from(saved);
    }

    @Transactional
    public ProjectFileResponse move(String projectId, String fileId, FileMoveRequest request) {
        ProjectFile file = requireFile(projectId, fileId);
        requirePermission(settings(projectId).getMovePolicy(), request.approved());
        String destination = storageService.normalizeRelativePath(request.destinationPath());
        if (ignoreReason(destination) != null || fileRepository
                .findByProject_IdAndRelativePath(projectId, destination).isPresent()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        storageService.move(projectId, file.getRelativePath(), destination);
        file.moveTo(destination, fileName(destination));
        FileDescriptor descriptor = describe(destination);
        file.refresh(fileName(destination), descriptor.extension(), descriptor.category(),
                descriptor.language(), file.getSizeBytes(), file.getSha256());
        sourceIndexService.reindex(file, storageService.resolveProjectFile(projectId, destination));
        return ProjectFileResponse.from(file);
    }

    @Transactional
    public void delete(String projectId, String fileId, boolean approved) {
        ProjectFile file = requireFile(projectId, fileId);
        requirePermission(settings(projectId).getDeletePolicy(), approved);
        symbolRepository.deleteAllByProjectFile_Id(fileId);
        fileRepository.delete(file);
        storageService.delete(projectId, file.getRelativePath());
    }

    public Path projectRoot(String projectId) {
        projectService.requireProject(projectId);
        return storageService.projectSourceRoot(projectId);
    }

    private ProjectSettings settings(String projectId) {
        return settingsRepository.findByProject_Id(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_SETTINGS_NOT_FOUND));
    }

    private void requirePermission(PermissionPolicy policy, boolean approved) {
        if (policy == PermissionPolicy.DENY || (policy == PermissionPolicy.CONFIRM && !approved)) {
            throw new CustomException(ErrorCode.FILE_OPERATION_DENIED);
        }
    }

    private ProjectFile saveMetadata(Project project, ProjectFile current, String relativePath,
                                     long size, String sha256) {
        FileDescriptor descriptor = describe(relativePath);
        if (current == null) {
            current = ProjectFile.create(project, relativePath, fileName(relativePath),
                    descriptor.extension(), descriptor.category(), descriptor.language(), size, sha256);
        } else {
            current.refresh(fileName(relativePath), descriptor.extension(), descriptor.category(),
                    descriptor.language(), size, sha256);
        }
        return fileRepository.save(current);
    }

    private FileSyncResponse summarize(String projectId, List<FileSyncItemResponse> changes) {
        return new FileSyncResponse(count(changes, FileChangeType.ADDED),
                count(changes, FileChangeType.MODIFIED), count(changes, FileChangeType.UNCHANGED),
                count(changes, FileChangeType.DELETED), count(changes, FileChangeType.IGNORED),
                fileRepository.countByProject_Id(projectId), List.copyOf(changes));
    }

    private int count(List<FileSyncItemResponse> changes, FileChangeType type) {
        return (int) changes.stream().filter(change -> change.changeType() == type).count();
    }

    private void validateBatch(List<MultipartFile> files, List<String> relativePaths) {
        if (files == null || relativePaths == null || files.isEmpty()
                || files.size() != relativePaths.size() || files.size() > properties.maxFiles()) {
            throw new CustomException(ErrorCode.INVALID_REQUEST);
        }
        if (files.stream().anyMatch(file -> file.getSize() > properties.maxFileBytes())) {
            throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
        }
    }

    private Set<String> validateProjectedLimits(List<MultipartFile> files, List<String> paths,
                                                Map<String, ProjectFile> existing,
                                                boolean fullSync, String scope) {
        Set<String> accepted = new HashSet<>();
        Set<String> seen = new HashSet<>();
        long projectedBytes = existing.values().stream().mapToLong(ProjectFile::getSizeBytes).sum();
        long projectedCount = existing.size();
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            if (!seen.add(path)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            if (ignoreReason(path) != null) {
                continue;
            }
            accepted.add(path);
            ProjectFile current = existing.get(path);
            projectedBytes += files.get(index).getSize() - (current == null ? 0 : current.getSizeBytes());
            if (current == null) {
                projectedCount++;
            }
        }
        if (fullSync) {
            for (ProjectFile stale : existing.values()) {
                if (!accepted.contains(stale.getRelativePath()) && inScope(stale.getRelativePath(), scope)) {
                    projectedBytes -= stale.getSizeBytes();
                    projectedCount--;
                }
            }
        }
        if (projectedBytes > properties.maxProjectBytes() || projectedCount > properties.maxFiles()) {
            throw new CustomException(ErrorCode.FILE_LIMIT_EXCEEDED);
        }
        return accepted;
    }

    private String commonRoot(List<String> paths) {
        if (paths.isEmpty() || paths.stream().anyMatch(path -> !path.contains("/"))) {
            return null;
        }
        String first = paths.get(0).substring(0, paths.get(0).indexOf('/'));
        return paths.stream().allMatch(path -> path.startsWith(first + "/")) ? first : null;
    }

    private String stripRoot(String path, String root) {
        return root == null ? path : path.substring(root.length() + 1);
    }

    private String normalizeScope(String scope, String root) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        return stripRoot(storageService.normalizeRelativePath(scope), root);
    }

    private boolean inScope(String path, String scope) {
        return scope == null || path.equals(scope) || path.startsWith(scope + "/");
    }

    private String ignoreReason(String relativePath) {
        String lowercase = relativePath.toLowerCase(Locale.ROOT);
        String name = fileName(lowercase);
        if (SENSITIVE_NAMES.contains(name) || name.endsWith(".pem") || name.endsWith(".key")
                || name.endsWith(".p12") || (name.contains("service-account") && name.endsWith(".json"))) {
            return "민감정보 파일";
        }
        for (String segment : lowercase.split("/")) {
            if (properties.ignoredDirectories().stream().anyMatch(directory -> directory.equalsIgnoreCase(segment))) {
                return "제외 디렉터리";
            }
        }
        try {
            describe(relativePath);
            return null;
        } catch (CustomException exception) {
            return "지원하지 않는 바이너리 또는 파일 형식";
        }
    }

    private FileDescriptor describe(String relativePath) {
        String name = fileName(relativePath);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (SOURCE_EXTENSIONS.contains(extension)) {
            return new FileDescriptor(extension, FileCategory.SOURCE_CODE, language(extension));
        }
        if (TEXT_EXTENSIONS.contains(extension) || extension.isBlank()) {
            return new FileDescriptor(extension, FileCategory.TEXT, null);
        }
        if (CONFIG_EXTENSIONS.contains(extension)) {
            return new FileDescriptor(extension, FileCategory.CONFIGURATION, null);
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return new FileDescriptor(extension, FileCategory.DOCUMENT, null);
        }
        throw new CustomException(ErrorCode.UNSUPPORTED_FILE);
    }

    private String language(String extension) {
        return switch (extension) {
            case "java" -> "Java";
            case "kt", "kts" -> "Kotlin";
            case "py" -> "Python";
            case "js", "jsx" -> "JavaScript";
            case "ts", "tsx" -> "TypeScript";
            case "cs" -> "C#";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "rb" -> "Ruby";
            case "php" -> "PHP";
            default -> extension.toUpperCase(Locale.ROOT);
        };
    }

    private String fileName(String relativePath) {
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    private String hash(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("파일 해시 계산에 실패했습니다.", exception);
        }
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("파일 크기를 확인하지 못했습니다.", exception);
        }
    }

    private record FileDescriptor(String extension, FileCategory category, String language) {
    }
}
