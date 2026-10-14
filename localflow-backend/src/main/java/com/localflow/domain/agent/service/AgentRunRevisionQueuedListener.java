package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentRunRevisionQueuedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AgentRunRevisionQueuedListener {
    private final AgentExecutionService executionService;

    public AgentRunRevisionQueuedListener(AgentExecutionService executionService) {
        this.executionService = executionService;
    }

    @Async("agentTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AgentRunRevisionQueuedEvent event) {
        executionService.processRevision(event.projectId(), event.runId(), event.instruction());
    }
}
