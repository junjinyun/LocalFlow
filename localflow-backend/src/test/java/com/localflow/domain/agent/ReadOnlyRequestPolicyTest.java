package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentPlan;
import com.localflow.domain.agent.domain.FileOperationAction;
import com.localflow.domain.agent.domain.FileOperationPlan;
import com.localflow.domain.agent.service.ReadOnlyRequestPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadOnlyRequestPolicyTest {
    private final AgentPlan mutationPlan = new AgentPlan("분석", "분석 결과",
            List.of(new FileOperationPlan("op-1", FileOperationAction.UPDATE,
                    "src/Auth.java", null, "class Auth {}")));

    @Test
    void stripsOperationsFromInspectionResult() {
        AgentPlan guarded = ReadOnlyRequestPolicy.enforce(
                "인증 구조를 분석해줘", "INSPECT_FILES", mutationPlan);

        assertThat(guarded.operations()).isEmpty();
        assertThat(guarded.response()).isEqualTo("분석 결과");
    }

    @Test
    void stripsOperationsWhenOriginalPromptExplicitlyForbidsChanges() {
        AgentPlan guarded = ReadOnlyRequestPolicy.enforce(
                "추가 방법을 제안하되 파일은 변경하지 마", "CREATE_FILES", mutationPlan);

        assertThat(guarded.operations()).isEmpty();
    }

    @Test
    void keepsOperationsForMutationRequest() {
        AgentPlan guarded = ReadOnlyRequestPolicy.enforce(
                "인증 코드를 수정해줘", "MODIFY_FILES", mutationPlan);

        assertThat(guarded.operations()).hasSize(1);
    }
}
