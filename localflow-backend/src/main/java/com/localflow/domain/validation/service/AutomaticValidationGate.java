package com.localflow.domain.validation.service;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.service.AgentRunProgressService;
import com.localflow.domain.project.entity.PermissionPolicy;
import com.localflow.domain.project.entity.ProjectSettings;
import com.localflow.domain.validation.domain.ValidationCommandSpec;
import com.localflow.domain.validation.domain.ValidationExecutionStatus;
import com.localflow.domain.validation.dto.ValidationCommandResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AutomaticValidationGate {
    private final ProjectValidationDiscoveryService discoveryService;
    private final ProjectValidationExecutionService executionService;
    private final AgentRunProgressService progressService;

    public AutomaticValidationGate(ProjectValidationDiscoveryService discoveryService,
                                   ProjectValidationExecutionService executionService,
                                   AgentRunProgressService progressService) {
        this.discoveryService = discoveryService;
        this.executionService = executionService;
        this.progressService = progressService;
    }

    public void validate(String projectId, String runId, ProjectSettings settings) {
        if (!settings.isAutoValidationEnabled()) return;
        if (settings.getExecutePolicy() == PermissionPolicy.DENY) {
            throw new IllegalStateException("자동 코드 검증이 켜져 있지만 코드 실행 권한이 허용되지 않았습니다.");
        }
        List<ValidationCommandSpec> commands = discoveryService.discover(projectId);
        if (commands.isEmpty()) {
            progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                    "실행 가능한 빌드·테스트 명령이 없어 자동 검증을 건너뛰었습니다.");
            return;
        }
        progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                "파일 적용 전 자동 검증 " + commands.size() + "건을 실행합니다.");
        for (ValidationCommandSpec command : commands) {
            ValidationCommandResult result = executionService.executeForGate(projectId, runId, command.id());
            if (result.status() != ValidationExecutionStatus.SUCCESS) {
                throw new IllegalStateException(command.displayName()
                        + " 검증에 실패하여 파일 변경을 적용하지 않았습니다.");
            }
        }
        progressService.append(projectId, runId, AgentProgressStage.VALIDATING,
                "자동 코드 검증을 모두 통과했습니다.");
    }
}
