package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentProgressStage;
import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.agent.repository.AgentRunRepository;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AgentRunRecoveryService {
    private static final String RESTART_MESSAGE = "서버가 재시작되어 진행 중이던 작업을 종료했습니다. 다시 실행해 주세요.";
    private static final List<AgentRunStatus> IN_FLIGHT_STATUSES = List.of(
            AgentRunStatus.PENDING,
            AgentRunStatus.DECIDING,
            AgentRunStatus.PLANNING,
            AgentRunStatus.VALIDATING,
            AgentRunStatus.RUNNING,
            AgentRunStatus.CANCEL_REQUESTED
    );

    private final AgentRunRepository runRepository;
    private final AgentRunProgressService progressService;
    private final AgentRunCancellationService cancellationService;

    public AgentRunRecoveryService(AgentRunRepository runRepository,
                                   AgentRunProgressService progressService,
                                   AgentRunCancellationService cancellationService) {
        this.runRepository = runRepository;
        this.progressService = progressService;
        this.cancellationService = cancellationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedRuns() {
        for (AgentRun run : runRepository.findAllByStatusIn(IN_FLIGHT_STATUSES)) {
            if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
                cancellationService.complete(run.getProject().getId(), run.getId());
                continue;
            }
            run.fail(RESTART_MESSAGE);
            runRepository.saveAndFlush(run);
            progressService.append(run.getProject().getId(), run.getId(), AgentProgressStage.FAILED,
                    RESTART_MESSAGE);
        }
    }
}
