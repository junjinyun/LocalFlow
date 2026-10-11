package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentRunStatus;
import com.localflow.domain.agent.domain.ExecutionMode;
import com.localflow.domain.agent.entity.AgentRun;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.agent.dto.response.AgentRunResponse;
import org.junit.jupiter.api.Test;

class AgentRunResumeTest {
    @Test
    void resumesApprovedPlanWithoutLosingStoredExecutionData() {
        AgentRun run = AgentRun.draft(Project.create("sample", null), "파일을 수정해줘",
                ExecutionMode.CONFIRM_EVERY_STEP, AiProviderType.OLLAMA,
                "qwen2.5-coder:3b");
        String decision = "{\"needsHumanApproval\":true}";
        String plan = "{\"operations\":[]}";
        String changes = "[]";

        run.waitForApproval(decision, plan, changes, "승인 필요",
                "qwen2.5-coder:3b", 100, 20);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.WAITING_APPROVAL);
        assertThat(run.getPlanJson()).isEqualTo(plan);

        run.startRunning(changes);
        run.complete(decision, plan, "승인된 작업 완료",
                "qwen2.5-coder:3b", 100, 20);

        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.getDecisionJson()).isEqualTo(decision);
        assertThat(run.getPlanJson()).isEqualTo(plan);
        assertThat(run.getChangesJson()).isEqualTo(changes);
        assertThat(run.getCompletedAt()).isNotNull();
    }

    @Test
    void storesSafeProviderErrorMetadataForRunDetails() {
        AgentRun run = AgentRun.draft(Project.create("sample", null), "AI 호출",
                ExecutionMode.BALANCED, AiProviderType.OPENAI, "gpt-test");

        run.failProvider("OpenAI 인증에 실패했습니다.",
                ProviderErrorCode.AUTHENTICATION_FAILED, false,
                "API 키를 다시 확인해 주세요.");
        AgentRunResponse response = AgentRunResponse.from(run);

        assertThat(response.errorMessage()).isEqualTo("OpenAI 인증에 실패했습니다.");
        assertThat(response.providerErrorCode()).isEqualTo(ProviderErrorCode.AUTHENTICATION_FAILED);
        assertThat(response.errorRetryable()).isFalse();
        assertThat(response.errorRecommendedAction()).contains("API 키");
    }
}
