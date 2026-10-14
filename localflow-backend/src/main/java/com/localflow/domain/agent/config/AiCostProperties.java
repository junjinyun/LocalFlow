package com.localflow.domain.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "localflow.ai-cost")
public record AiCostProperties(String currency, String rates) {
    @ConstructorBinding
    public AiCostProperties {
        currency = currency == null || currency.isBlank() ? "USD" : currency.strip().toUpperCase();
        rates = rates == null ? "" : rates.strip();
    }
}
