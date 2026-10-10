package com.localflow.domain.agent.controller;

import com.localflow.domain.agent.dto.request.AgentRunCreateRequest;
import com.localflow.domain.agent.dto.request.AgentRunExecuteRequest;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import com.localflow.domain.agent.dto.response.AgentRunProgressResponse;
import com.localflow.domain.agent.dto.response.AgentRunSummaryResponse;
import com.localflow.domain.agent.service.AgentExecutionService;
import com.localflow.domain.agent.service.AgentRunCancellationService;
import com.localflow.domain.agent.service.AgentRunDispatchService;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.agent.service.AgentRunService;
import com.localflow.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Agent Runs", description = "프로젝트별 에이전트 실행")
@RestController
@RequestMapping("/api/projects/{projectId}/agent-runs")
public class AgentRunController {
    private final AgentRunService agentRunService;
    private final AgentExecutionService agentExecutionService;
    private final AgentRunCancellationService cancellationService;
    private final AgentRunDispatchService dispatchService;
    private final AgentRunProgressService progressService;

    public AgentRunController(AgentRunService agentRunService,
                              AgentExecutionService agentExecutionService,
                              AgentRunCancellationService cancellationService,
                              AgentRunDispatchService dispatchService,
                              AgentRunProgressService progressService) {
        this.agentRunService = agentRunService;
        this.agentExecutionService = agentExecutionService;
        this.cancellationService = cancellationService;
        this.dispatchService = dispatchService;
        this.progressService = progressService;
    }

    @Operation(summary = "에이전트 비동기 실행 요청 또는 승인 후 작업 재개")
    @PostMapping("/{runId}/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AgentRunResponse> execute(@PathVariable String projectId,
                                                @PathVariable String runId,
                                                @RequestBody(required = false) AgentRunExecuteRequest request) {
        boolean approved = request != null && request.isApproved();
        return ApiResponse.success(dispatchService.enqueue(projectId, runId, approved));
    }

    @Operation(summary = "에이전트 실행 초안 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AgentRunResponse> createDraft(
            @PathVariable String projectId,
            @Valid @RequestBody AgentRunCreateRequest request
    ) {
        return ApiResponse.success(
                agentRunService.createDraft(projectId, request),
                "에이전트 실행 초안이 생성되었습니다."
        );
    }

    @Operation(summary = "에이전트 실행 기록 목록 조회")
    @GetMapping
    public ApiResponse<List<AgentRunSummaryResponse>> findAll(@PathVariable String projectId) {
        return ApiResponse.success(agentRunService.findAll(projectId));
    }

    @Operation(summary = "프로젝트의 실행 중 또는 승인 대기 작업 조회")
    @GetMapping("/active")
    public ApiResponse<List<AgentRunResponse>> findActive(@PathVariable String projectId) {
        return ApiResponse.success(agentRunService.findActive(projectId));
    }

    @Operation(summary = "에이전트 실행 기록 단건 조회")
    @GetMapping("/{runId}")
    public ApiResponse<AgentRunResponse> findById(@PathVariable String projectId,
                                                 @PathVariable String runId) {
        return ApiResponse.success(agentExecutionService.findWithSnapshot(projectId, runId));
    }

    @Operation(summary = "에이전트 실행 진행 이벤트 조회")
    @GetMapping("/{runId}/progress")
    public ApiResponse<List<AgentRunProgressResponse>> findProgress(@PathVariable String projectId,
                                                                   @PathVariable String runId) {
        return ApiResponse.success(progressService.findAll(projectId, runId));
    }

    @Operation(summary = "에이전트 실행 취소 요청")
    @PostMapping("/{runId}/cancel")
    public ApiResponse<AgentRunResponse> cancel(@PathVariable String projectId,
                                               @PathVariable String runId) {
        return ApiResponse.success(cancellationService.request(projectId, runId));
    }
}
