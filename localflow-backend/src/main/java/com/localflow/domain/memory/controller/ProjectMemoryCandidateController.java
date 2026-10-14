package com.localflow.domain.memory.controller;

import com.localflow.domain.memory.dto.MemoryCandidateApproveRequest;
import com.localflow.domain.memory.dto.MemoryCandidateResponse;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.service.ProjectMemoryCandidateService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Memory Candidate", description = "대화와 실행 결과에서 찾은 프로젝트 기억 후보")
@RestController
@RequestMapping("/api/projects/{projectId}/memory-candidates")
public class ProjectMemoryCandidateController {
    private final ProjectMemoryCandidateService service;

    public ProjectMemoryCandidateController(ProjectMemoryCandidateService service) {
        this.service = service;
    }

    @Operation(summary = "프로젝트 기억 후보 조회")
    @GetMapping
    public ApiResponse<List<MemoryCandidateResponse>> findAll(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "true") boolean pendingOnly) {
        return ApiResponse.success(service.findAll(projectId, pendingOnly));
    }

    @Operation(summary = "대화와 완료된 실행에서 새 기억 후보 추출")
    @PostMapping("/extract")
    public ApiResponse<List<MemoryCandidateResponse>> extract(@PathVariable String projectId) {
        return ApiResponse.success(service.extract(projectId));
    }

    @Operation(summary = "후보를 편집하여 프로젝트 기억으로 승인")
    @PostMapping("/{candidateId}/approve")
    public ApiResponse<ProjectMemoryResponse> approve(
            @PathVariable String projectId, @PathVariable String candidateId,
            @Valid @RequestBody MemoryCandidateApproveRequest request) {
        return ApiResponse.success(service.approve(projectId, candidateId, request));
    }

    @Operation(summary = "프로젝트 기억 후보 거절")
    @PostMapping("/{candidateId}/reject")
    public ApiResponse<MemoryCandidateResponse> reject(
            @PathVariable String projectId, @PathVariable String candidateId) {
        return ApiResponse.success(service.reject(projectId, candidateId));
    }
}
