package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import org.junit.jupiter.api.Test;

class AgentRunPartialCompletionTest {
    @Test
    void preservesSuccessfulResultWithPartialTerminalStatus() {
        AgentRun run = AgentRun.draft(Project.create("partial", null), "복합 분석",
                ExecutionMode.BALANCED, AiProviderType.VERTEX_AI, "gemini-test");

        run.completePartially("{}", "{\"operations\":[]}",
                "3개 중 2개를 완료했습니다.", "gemini-test", 10, 20);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.PARTIALLY_COMPLETED);
        assertThat(run.getResultMessage()).contains("2개를 완료");
        assertThat(run.getErrorMessage()).isNull();
        assertThat(run.getCompletedAt()).isNotNull();
    }
}
