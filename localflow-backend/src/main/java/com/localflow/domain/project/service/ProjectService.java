package com.localflow.domain.project.service;

import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.request.ProjectSettingsUpdateRequest;
import com.localflow.domain.project.dto.request.ProjectUpdateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.dto.response.ProjectSettingsResponse;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.entity.ProjectSettings;
import com.localflow.domain.project.repository.ProjectRepository;
import com.localflow.domain.project.repository.ProjectSettingsRepository;
import com.localflow.domain.workspace.service.WorkspaceStorageService;
import com.localflow.domain.workspace.repository.CodeSymbolRepository;
import com.localflow.domain.workspace.repository.ProjectFileRepository;
import com.localflow.domain.chat.repository.ChatMessageRepository;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.agent.repository.AgentRunRepository;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectService {
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("[a-z0-9][a-z0-9._+-]{0,19}");
    private final ProjectRepository projectRepository;
    private final ProjectSettingsRepository settingsRepository;
    private final WorkspaceStorageService storageService;
    private final CodeSymbolRepository symbolRepository;
    private final ProjectFileRepository fileRepository;
    private final ChatMessageRepository chatRepository;
    private final ProjectMemoryRepository memoryRepository;
    private final AgentRunRepository agentRunRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectSettingsRepository settingsRepository,
                          WorkspaceStorageService storageService,
                          CodeSymbolRepository symbolRepository,
                          ProjectFileRepository fileRepository,
                          ChatMessageRepository chatRepository,
                          ProjectMemoryRepository memoryRepository,
                          AgentRunRepository agentRunRepository) {
        this.projectRepository = projectRepository;
        this.settingsRepository = settingsRepository;
        this.storageService = storageService;
        this.symbolRepository = symbolRepository;
        this.fileRepository = fileRepository;
        this.chatRepository = chatRepository;
        this.memoryRepository = memoryRepository;
        this.agentRunRepository = agentRunRepository;
    }

    @Transactional
    public ProjectResponse create(ProjectCreateRequest request) {
        Project project = Project.create(request.name().strip(), normalize(request.description()));
        projectRepository.save(project);
        settingsRepository.save(ProjectSettings.defaults(project));
        return ProjectResponse.from(project);
    }

    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse findById(String projectId) {
        return ProjectResponse.from(requireProject(projectId));
    }

    @Transactional
    public ProjectResponse update(String projectId, ProjectUpdateRequest request) {
        Project project = requireProject(projectId);
        project.update(request.name().strip(), normalize(request.description()));
        return ProjectResponse.from(project);
    }

    public ProjectSettingsResponse findSettings(String projectId) {
        requireProject(projectId);
        return ProjectSettingsResponse.from(settingsRepository.findByProject_Id(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_SETTINGS_NOT_FOUND)));
    }

    @Transactional
    public ProjectSettingsResponse updateSettings(String projectId, ProjectSettingsUpdateRequest request) {
        requireProject(projectId);
        ProjectSettings settings = settingsRepository.findByProject_Id(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_SETTINGS_NOT_FOUND));
        settings.update(request.executionMode(), request.privacyMode(), request.readPolicy(),
                request.createPolicy(), request.editPolicy(), request.movePolicy(),
                request.deletePolicy(), request.executePolicy(),
                normalizeExcludedPaths(request.excludedPathPatterns()),
                normalizeExcludedExtensions(request.excludedExtensions()));
        return ProjectSettingsResponse.from(settings);
    }

    @Transactional
    public void delete(String projectId) {
        Project project = requireProject(projectId);
        storageService.deleteProject(projectId);
        symbolRepository.deleteAllByProjectFile_Project_Id(projectId);
        fileRepository.deleteAllByProject_Id(projectId);
        chatRepository.deleteAllByProject_Id(projectId);
        memoryRepository.deleteAllByProject_Id(projectId);
        agentRunRepository.deleteAllByProject_Id(projectId);
        settingsRepository.deleteAllByProject_Id(projectId);
        projectRepository.delete(project);
    }

    public Project requireProject(String projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Set<String> normalizeExcludedPaths(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) return normalized;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String path = value.strip().replace('\\', '/');
            if (path.startsWith("/") || path.contains("..") || path.contains(":")
                    || path.chars().anyMatch(Character::isISOControl)) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            while (path.startsWith("./")) path = path.substring(2);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.isBlank()) throw new CustomException(ErrorCode.INVALID_REQUEST);
            normalized.add(path);
        }
        return normalized;
    }

    private Set<String> normalizeExcludedExtensions(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        if (values == null) return normalized;
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String extension = value.strip().toLowerCase(Locale.ROOT);
            while (extension.startsWith(".")) extension = extension.substring(1);
            if (!EXTENSION_PATTERN.matcher(extension).matches()) {
                throw new CustomException(ErrorCode.INVALID_REQUEST);
            }
            normalized.add(extension);
        }
        return normalized;
    }
}
