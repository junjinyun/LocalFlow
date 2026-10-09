package com.localflow.domain.memory.service;

import com.localflow.domain.memory.dto.ProjectMemoryCreateRequest;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.dto.ProjectMemoryUpdateRequest;
import com.localflow.domain.memory.entity.ProjectMemory;
import com.localflow.domain.memory.repository.ProjectMemoryRepository;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.project.service.ProjectService;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProjectMemoryService {
    private final ProjectMemoryRepository repository;
    private final ProjectService projectService;

    public ProjectMemoryService(ProjectMemoryRepository repository, ProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    @Transactional
    public ProjectMemoryResponse create(String projectId, ProjectMemoryCreateRequest request) {
        Project project = projectService.requireProject(projectId);
        ProjectMemory memory = ProjectMemory.create(project, request.type(),
                request.title().strip(), request.content().strip());
        return ProjectMemoryResponse.from(repository.save(memory));
    }

    public List<ProjectMemoryResponse> findAll(String projectId, boolean activeOnly) {
        projectService.requireProject(projectId);
        List<ProjectMemory> memories = activeOnly
                ? repository.findAllByProject_IdAndActiveTrueOrderByUpdatedAtDesc(projectId)
                : repository.findAllByProject_IdOrderByUpdatedAtDesc(projectId);
        return memories.stream().map(ProjectMemoryResponse::from).toList();
    }

    @Transactional
    public ProjectMemoryResponse update(String projectId, String memoryId,
                                        ProjectMemoryUpdateRequest request) {
        ProjectMemory memory = requireMemory(projectId, memoryId);
        memory.update(request.type(), request.title().strip(), request.content().strip(), request.active());
        return ProjectMemoryResponse.from(memory);
    }

    @Transactional
    public void delete(String projectId, String memoryId) {
        repository.delete(requireMemory(projectId, memoryId));
    }

    private ProjectMemory requireMemory(String projectId, String memoryId) {
        ProjectMemory memory = repository.findById(memoryId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMORY_NOT_FOUND));
        if (!memory.getProject().getId().equals(projectId)) {
            throw new CustomException(ErrorCode.MEMORY_NOT_FOUND);
        }
        return memory;
    }
}
