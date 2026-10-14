package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.config.AiCostProperties;
import com.localflow.domain.agent.domain.AgentUsageSummary.CostStatus;
import com.localflow.domain.agent.service.AiUsageCostService;
import com.localflow.domain.provider.domain.AiProviderType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AiUsageCostServiceTest {
    private final AiUsageCostService service = new AiUsageCostService(new AiCostProperties(
            "USD", "OPENAI|gpt-test|1.00|2.00;invalid-entry"));

    @Test
    void calculatesConfiguredModelCostPerMillionTokens() {
        var result = service.summarize(AiProviderType.OPENAI, "gpt-test",
                1_000, 2_000, 500);

        assertThat(result.costStatus()).isEqualTo(CostStatus.PRICED);
        assertThat(result.estimatedCost()).isEqualByComparingTo(new BigDecimal("0.00100000"));
        assertThat(result.actualCost()).isEqualByComparingTo(new BigDecimal("0.00300000"));
    }

    @Test
    void distinguishesLocalFreeModelFromUnknownExternalPrice() {
        var local = service.summarize(AiProviderType.OLLAMA, "qwen", 100, 90, 10);
        var unknown = service.summarize(AiProviderType.VERTEX_AI, "unknown", 100, 90, 10);

        assertThat(local.costStatus()).isEqualTo(CostStatus.FREE);
        assertThat(local.actualCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(unknown.costStatus()).isEqualTo(CostStatus.UNAVAILABLE);
        assertThat(unknown.actualCost()).isNull();
    }
}
