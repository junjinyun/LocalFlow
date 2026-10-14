package com.localflow.domain.validation.controller;

import com.localflow.domain.validation.dto.ProjectValidationPreviewResponse;
import com.localflow.domain.validation.service.ProjectValidationDiscoveryService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Project Validation", description = "업로드된 프로젝트의 제한된 빌드·테스트 검증")
@RestController
@RequestMapping("/api/projects/{projectId}/validation")
public class ProjectValidationController {
    private final ProjectValidationDiscoveryService discoveryService;

    public ProjectValidationController(ProjectValidationDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @Operation(summary = "지원 빌드 도구 및 검증 명령 미리보기")
    @GetMapping("/preview")
    public ApiResponse<ProjectValidationPreviewResponse> preview(@PathVariable String projectId) {
        return ApiResponse.success(discoveryService.preview(projectId));
    }
}
