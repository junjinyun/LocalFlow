package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.localflow.domain.agent.domain.DecomposedTask;
import com.localflow.domain.agent.domain.TaskDecomposition;
import com.localflow.domain.agent.service.AgentDecisionService;
import com.localflow.domain.agent.service.PromptTaskDecomposer;
import com.localflow.domain.project.entity.Project;
import com.localflow.domain.provider.config.ProviderRetryProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionResult;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.provider.domain.TaskScope;
import com.localflow.domain.provider.exception.ProviderCallException;
import com.localflow.domain.provider.port.DecisionProvider;
import com.localflow.domain.provider.service.ProviderRetryExecutor;
import com.localflow.domain.workspace.domain.FileCategory;
import com.localflow.domain.workspace.entity.ProjectFile;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDecisionServiceTest {
    private final PromptTaskDecomposer decomposer = new PromptTaskDecomposer();
    private final ProviderRetryExecutor retryExecutor = new ProviderRetryExecutor(
            new ProviderRetryProperties(1, Duration.ofMillis(1), Duration.ofMillis(1)));

    @Test
    void replacesUnknownJevTagsWithSafeLocalDecision() {
        DecisionProvider provider = providerReturning(new DecisionResult(
                "MODIFY_FILES", "src/Auth.java", List.of("invented-tag"),
                List.of(new TaskScope("인증 수정", List.of("invented-tag"))),
                "MEDIUM", true, "jev", "{}"));
        AgentDecisionService service = new AgentDecisionService(List.of(provider), decomposer, retryExecutor);

        DecisionResult result = service.decide("인증 수정", List.of(file("src/Auth.java", "authentication")),
                true, decomposition());

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackProviderErrorCode()).isEqualTo(ProviderErrorCode.INVALID_RESPONSE);
        assertThat(result.selectedTags()).doesNotContain("invented-tag");
        assertThat(result.model()).isEqualTo("LOCAL_HEURISTIC");
    }

    @Test
    void preservesTimeoutReasonWhenFallingBack() {
        DecisionProvider provider = mock(DecisionProvider.class);
        when(provider.providerType()).thenReturn(AiProviderType.JEV);
        when(provider.available()).thenReturn(true);
        when(provider.decide(any())).thenThrow(ProviderCallException.timeout(
                AiProviderType.JEV, "JEV 요청 시간이 초과되었습니다."));
        AgentDecisionService service = new AgentDecisionService(List.of(provider), decomposer, retryExecutor);

        DecisionResult result = service.decide("인증 수정", List.of(file("src/Auth.java", "authentication")),
                true, decomposition());

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.fallbackProviderErrorCode()).isEqualTo(ProviderErrorCode.TIMEOUT);
        assertThat(result.fallbackReason()).contains("TIMEOUT");
        assertThat(result.actionType()).isEqualTo("MODIFY_FILES");
    }

    private DecisionProvider providerReturning(DecisionResult result) {
        DecisionProvider provider = mock(DecisionProvider.class);
        when(provider.providerType()).thenReturn(AiProviderType.JEV);
        when(provider.available()).thenReturn(true);
        when(provider.decide(any())).thenReturn(result);
        return provider;
    }

    private ProjectFile file(String path, String tag) {
        ProjectFile file = ProjectFile.create(Project.create("sample", null), path, "Auth.java",
                "java", FileCategory.SOURCE_CODE, "java", 10, "hash");
        file.replaceTags(List.of(tag));
        return file;
    }

    private TaskDecomposition decomposition() {
        return new TaskDecomposition("인증 수정", "LOCAL", List.of(new DecomposedTask(
                "task-001", "인증 수정", List.of(), List.of("authentication"), List.of())));
    }
}
