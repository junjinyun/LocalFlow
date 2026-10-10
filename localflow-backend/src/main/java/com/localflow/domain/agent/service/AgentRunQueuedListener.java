package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentRunQueuedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgentRunQueuedListener {
    private final AgentExecutionService executionService;

    public AgentRunQueuedListener(AgentExecutionService executionService) {
        this.executionService = executionService;
    }

    @Async("agentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AgentRunQueuedEvent event) {
        executionService.processQueued(event.projectId(), event.runId(),
                event.approved(), event.approvalResume());
    }
}
