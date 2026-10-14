package com.localflow.domain.agent.domain;

import com.localflow.domain.provider.domain.AiProviderType;
import java.math.BigDecimal;

public record AgentUsageSummary(
        AiProviderType provider,
        String model,
        Integer estimatedInputTokens,
        Integer actualInputTokens,
        Integer actualOutputTokens,
        CostStatus costStatus,
        String currency,
        BigDecimal inputRatePerMillion,
        BigDecimal outputRatePerMillion,
        BigDecimal estimatedCost,
        BigDecimal actualCost
) {
    public enum CostStatus { PRICED, FREE, UNAVAILABLE }
}
