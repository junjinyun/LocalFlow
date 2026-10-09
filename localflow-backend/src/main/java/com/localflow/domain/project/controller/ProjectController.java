package com.localflow.domain.project.controller;

import com.localflow.global.common.ApiResponse;
import com.localflow.domain.project.dto.request.ProjectCreateRequest;
import com.localflow.domain.project.dto.request.ProjectSettingsUpdateRequest;
import com.localflow.domain.project.dto.request.ProjectUpdateRequest;
import com.localflow.domain.project.dto.response.ProjectResponse;
import com.localflow.domain.project.dto.response.ProjectSettingsResponse;
import com.localflow.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Projects", description = "프로젝트 작업공간 관리")
@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "프로젝트 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> create(@Valid @RequestBody ProjectCreateRequest request) {
        return ApiResponse.success(projectService.create(request), "프로젝트가 생성되었습니다.");
    }

    @Operation(summary = "프로젝트 목록 조회")
    @GetMapping
    public ApiResponse<List<ProjectResponse>> findAll() {
        return ApiResponse.success(projectService.findAll());
    }

    @Operation(summary = "프로젝트 단건 조회")
    @GetMapping("/{projectId}")
    public ApiResponse<ProjectResponse> findById(@PathVariable String projectId) {
        return ApiResponse.success(projectService.findById(projectId));
    }

    @Operation(summary = "프로젝트 정보 수정")
    @PutMapping("/{projectId}")
    public ApiResponse<ProjectResponse> update(@PathVariable String projectId,
                                               @Valid @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.success(projectService.update(projectId, request));
    }

    @Operation(summary = "프로젝트 권한 설정 조회")
    @GetMapping("/{projectId}/settings")
    public ApiResponse<ProjectSettingsResponse> findSettings(@PathVariable String projectId) {
        return ApiResponse.success(projectService.findSettings(projectId));
    }

    @Operation(summary = "프로젝트 권한 설정 수정")
    @PutMapping("/{projectId}/settings")
    public ApiResponse<ProjectSettingsResponse> updateSettings(
            @PathVariable String projectId,
            @Valid @RequestBody ProjectSettingsUpdateRequest request) {
        return ApiResponse.success(projectService.updateSettings(projectId, request));
    }

    @Operation(summary = "프로젝트 삭제")
    @DeleteMapping("/{projectId}")
    public ApiResponse<Void> delete(@PathVariable String projectId) {
        projectService.delete(projectId);
        return ApiResponse.success(null, "프로젝트가 삭제되었습니다.");
    }
}
