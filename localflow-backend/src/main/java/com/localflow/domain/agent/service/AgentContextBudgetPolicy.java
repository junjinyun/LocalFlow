package com.localflow.domain.agent.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AgentContextBudgetPolicy {
    private static final int MIN_CONTEXT_CHARS = 4_000;
    private static final int PROMPT_RESERVE_CHARS = 8_000;
    private final AiProviderProperties properties;

    public AgentContextBudgetPolicy(AiProviderProperties properties) {
        this.properties = properties;
    }

    public Budget resolve(AiProviderType provider, String model) {
        int configuredContext = properties.contextMaxChars();
        int maxInput = configuredContext + PROMPT_RESERVE_CHARS;
        if (provider == AiProviderType.OLLAMA && properties.ollama() != null) {
            int ollamaInput = Math.max(MIN_CONTEXT_CHARS + PROMPT_RESERVE_CHARS,
                    properties.ollama().numCtx() * 3);
            maxInput = Math.min(maxInput, ollamaInput);
            String normalizedModel = model == null ? "" : model.toLowerCase();
            if (normalizedModel.contains(":1b") || normalizedModel.contains(":2b")
                    || normalizedModel.contains(":3b") || normalizedModel.contains("-1b")
                    || normalizedModel.contains("-2b") || normalizedModel.contains("-3b")) {
                maxInput = Math.min(maxInput, 24_000);
            }
        }
        int contextChars = Math.min(configuredContext, maxInput - PROMPT_RESERVE_CHARS);
        if (contextChars < MIN_CONTEXT_CHARS) {
            throw new CustomException(ErrorCode.CONTEXT_BUDGET_EXCEEDED);
        }
        return new Budget(maxInput, contextChars);
    }

    public void validateRequest(Budget budget, String systemPrompt, String userPrompt) {
        int used = length(systemPrompt) + length(userPrompt);
        if (used > budget.maxInputChars()) {
            throw new CustomException(ErrorCode.CONTEXT_BUDGET_EXCEEDED);
        }
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    public record Budget(int maxInputChars, int maxContextChars) {
    }
}
