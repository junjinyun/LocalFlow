package com.localflow.domain.memory.controller;

import com.localflow.domain.memory.dto.ProjectMemoryCreateRequest;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.dto.ProjectMemoryUpdateRequest;
import com.localflow.domain.memory.service.ProjectMemoryService;
import com.localflow.global.common.ApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Memory", description = "AI 전처리에 활용할 프로젝트 규칙과 중요 맥락")
@RestController
@RequestMapping("/api/projects/{projectId}/memories")
public class ProjectMemoryController {
    private final ProjectMemoryService service;

    public ProjectMemoryController(ProjectMemoryService service) {
        this.service = service;
    }

    @Operation(summary = "프로젝트 기억 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectMemoryResponse> create(@PathVariable String projectId,
                                                     @Valid @RequestBody ProjectMemoryCreateRequest request) {
        return ApiResponse.success(service.create(projectId, request));
    }

    @Operation(summary = "프로젝트 기억 조회")
    @GetMapping
    public ApiResponse<List<ProjectMemoryResponse>> findAll(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ApiResponse.success(service.findAll(projectId, activeOnly));
    }

    @Operation(summary = "프로젝트 기억 수정 또는 활성화 설정")
    @PutMapping("/{memoryId}")
    public ApiResponse<ProjectMemoryResponse> update(@PathVariable String projectId,
                                                     @PathVariable String memoryId,
                                                     @Valid @RequestBody ProjectMemoryUpdateRequest request) {
        return ApiResponse.success(service.update(projectId, memoryId, request));
    }

    @Operation(summary = "프로젝트 기억 삭제")
    @DeleteMapping("/{memoryId}")
    public ApiResponse<Void> delete(@PathVariable String projectId, @PathVariable String memoryId) {
        service.delete(projectId, memoryId);
        return ApiResponse.success(null);
    }
}
