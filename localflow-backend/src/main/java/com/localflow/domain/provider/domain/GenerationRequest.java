package com.localflow.domain.provider.domain;

public record GenerationRequest(String systemPrompt, String userPrompt, String model) {
}
