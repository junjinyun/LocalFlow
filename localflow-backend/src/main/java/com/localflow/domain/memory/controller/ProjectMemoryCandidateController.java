package com.localflow.domain.memory.controller;

import com.localflow.domain.memory.dto.MemoryCandidateApproveRequest;
import com.localflow.domain.memory.dto.MemoryCandidateResponse;
import com.localflow.domain.memory.dto.MemoryCandidateExtractionHistoryResponse;
import com.localflow.domain.memory.dto.ProjectMemoryResponse;
import com.localflow.domain.memory.domain.MemoryCandidateExtractionTrigger;
import com.localflow.domain.memory.service.MemoryCandidateExtractionHistoryService;
import com.localflow.domain.memory.service.ProjectMemoryCandidateService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(ProjectMemoryCandidateController.class);

    private final ProjectMemoryCandidateService service;
    private final MemoryCandidateExtractionHistoryService historyService;

    public ProjectMemoryCandidateController(ProjectMemoryCandidateService service,
                                            MemoryCandidateExtractionHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
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
        Instant startedAt = Instant.now();
        try {
            List<MemoryCandidateResponse> created = service.extract(projectId);
            recordManualSuccess(projectId, created.size(), startedAt);
            return ApiResponse.success(created);
        } catch (RuntimeException exception) {
            recordManualFailure(projectId, exception, startedAt);
            throw exception;
        }
    }

    @Operation(summary = "최근 기억 후보 추출 성공·실패 이력 조회")
    @GetMapping("/extractions")
    public ApiResponse<List<MemoryCandidateExtractionHistoryResponse>> findExtractionHistory(
            @PathVariable String projectId) {
        return ApiResponse.success(historyService.findRecent(projectId));
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

    private void recordManualSuccess(String projectId, int createdCount, Instant startedAt) {
        try {
            historyService.recordSuccess(projectId, MemoryCandidateExtractionTrigger.MANUAL,
                    null, null, createdCount, startedAt);
        } catch (RuntimeException exception) {
            log.warn("수동 기억 후보 추출 성공 이력을 저장하지 못했습니다. projectId={}",
                    projectId, exception);
        }
    }

    private void recordManualFailure(String projectId, RuntimeException cause, Instant startedAt) {
        try {
            historyService.recordFailure(projectId, MemoryCandidateExtractionTrigger.MANUAL,
                    null, null, cause.getMessage(), startedAt);
        } catch (RuntimeException exception) {
            log.warn("수동 기억 후보 추출 실패 이력을 저장하지 못했습니다. projectId={}",
                    projectId, exception);
        }
    }
}
