package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentTaskExecution;
import com.localflow.domain.agent.domain.AgentTaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentTaskExecutionTest {
    @Test
    void recordsTaskDurationAndStructuredGenerationAttempts() {
        AgentTaskExecution pending = new AgentTaskExecution("task-1", "인증 분석", List.of(), 1,
                AgentTaskStatus.PENDING, null, null, null, null,
                null, null, null, null);

        AgentTaskExecution completed = pending
                .withStatus(AgentTaskStatus.RUNNING, null, null, null, null)
                .withGenerationAttempts(2)
                .withStatus(AgentTaskStatus.COMPLETED, "{}", "[]", "완료", null);

        assertThat(completed.startedAt()).isNotNull();
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.durationMillis()).isNotNegative();
        assertThat(completed.generationAttempts()).isEqualTo(2);
    }
}
