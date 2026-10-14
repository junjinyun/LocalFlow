package com.localflow.domain.agent.service;

import com.localflow.domain.agent.config.AiCostProperties;
import com.localflow.domain.agent.domain.AgentUsageSummary;
import com.localflow.domain.agent.domain.AgentUsageSummary.CostStatus;
import com.localflow.domain.provider.domain.AiProviderType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiUsageCostService {
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private final AiCostProperties properties;
    private final Map<String, Rate> rates;

    public AiUsageCostService(AiCostProperties properties) {
        this.properties = properties;
        this.rates = parse(properties.rates());
    }

    public AgentUsageSummary summarize(AiProviderType provider, String model,
                                       Integer estimatedInputTokens,
                                       Integer actualInputTokens, Integer actualOutputTokens) {
        if (provider == AiProviderType.OLLAMA) {
            return new AgentUsageSummary(provider, model, estimatedInputTokens,
                    actualInputTokens, actualOutputTokens, CostStatus.FREE,
                    properties.currency(), BigDecimal.ZERO, BigDecimal.ZERO,
                    zeroWhenKnown(estimatedInputTokens), zeroWhenKnown(actualInputTokens, actualOutputTokens));
        }
        Rate rate = rates.get(key(provider, model));
        if (rate == null) {
            return new AgentUsageSummary(provider, model, estimatedInputTokens,
                    actualInputTokens, actualOutputTokens, CostStatus.UNAVAILABLE,
                    properties.currency(), null, null, null, null);
        }
        return new AgentUsageSummary(provider, model, estimatedInputTokens,
                actualInputTokens, actualOutputTokens, CostStatus.PRICED,
                properties.currency(), rate.input(), rate.output(),
                cost(estimatedInputTokens, null, rate),
                cost(actualInputTokens, actualOutputTokens, rate));
    }

    private BigDecimal cost(Integer inputTokens, Integer outputTokens, Rate rate) {
        if (inputTokens == null && outputTokens == null) return null;
        BigDecimal input = BigDecimal.valueOf(inputTokens == null ? 0 : inputTokens)
                .multiply(rate.input());
        BigDecimal output = BigDecimal.valueOf(outputTokens == null ? 0 : outputTokens)
                .multiply(rate.output());
        return input.add(output).divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroWhenKnown(Integer... values) {
        for (Integer value : values) if (value != null) return BigDecimal.ZERO;
        return null;
    }

    private Map<String, Rate> parse(String configured) {
        Map<String, Rate> parsed = new HashMap<>();
        if (configured == null || configured.isBlank()) return Map.of();
        for (String entry : configured.split(";")) {
            String[] fields = entry.strip().split("\\|", -1);
            if (fields.length != 4) continue;
            try {
                AiProviderType provider = AiProviderType.valueOf(fields[0].strip().toUpperCase(Locale.ROOT));
                String model = fields[1].strip();
                BigDecimal input = new BigDecimal(fields[2].strip());
                BigDecimal output = new BigDecimal(fields[3].strip());
                if (!model.isBlank() && input.signum() >= 0 && output.signum() >= 0) {
                    parsed.put(key(provider, model), new Rate(input, output));
                }
            } catch (IllegalArgumentException ignored) {
                // 잘못된 단가 한 항목만 제외하고 나머지 설정은 계속 사용한다.
            }
        }
        return Map.copyOf(parsed);
    }

    private String key(AiProviderType provider, String model) {
        return provider.name() + "|" + (model == null ? "" : model.strip().toLowerCase(Locale.ROOT));
    }

    private record Rate(BigDecimal input, BigDecimal output) { }
}
