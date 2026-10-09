package com.localflow.domain.provider.domain;

public record GenerationResult(
        String text,
        String model,
        Integer inputTokens,
        Integer outputTokens
) {
}
