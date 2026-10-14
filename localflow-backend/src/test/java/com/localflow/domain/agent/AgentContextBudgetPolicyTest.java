package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.agent.service.AgentContextBudgetPolicy;
import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.global.error.CustomException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentContextBudgetPolicyTest {
    @Test
    void givesSmallLocalModelAConservativeBudget() {
        AiProviderProperties properties = new AiProviderProperties(32_000, 20, null, null,
                new AiProviderProperties.Ollama(true, "http://localhost:11434", "qwen:3b",
                        Duration.ofSeconds(5), Duration.ofSeconds(2)), null);
        AgentContextBudgetPolicy policy = new AgentContextBudgetPolicy(properties);

        var budget = policy.resolve(AiProviderType.OLLAMA, "qwen:3b");

        assertThat(budget.maxInputChars()).isEqualTo(24_000);
        assertThat(budget.maxContextChars()).isEqualTo(16_000);
    }

    @Test
    void rejectsRequestThatExceedsResolvedBudget() {
        AgentContextBudgetPolicy policy = new AgentContextBudgetPolicy(
                new AiProviderProperties(8_000, 20, null, null, null, null));
        var budget = policy.resolve(AiProviderType.OPENAI, "test");

        assertThatThrownBy(() -> policy.validateRequest(budget, "system", "x".repeat(20_000)))
                .isInstanceOf(CustomException.class);
    }
}
