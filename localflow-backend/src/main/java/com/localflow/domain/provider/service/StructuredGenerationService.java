package com.localflow.domain.provider.service;

import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import com.localflow.domain.provider.exception.StructuredOutputException;
import com.localflow.domain.provider.port.GenerationProvider;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class StructuredGenerationService {
    public <T> ParsedGeneration<T> generate(GenerationProvider provider,
                                            GenerationRequest request,
                                            Function<String, T> parser,
                                            String responseName) {
        int maxAttempts = provider.structuredOutputRetries() + 1;
        GenerationRequest currentRequest = request.withSchemaInstruction(responseName);
        RuntimeException lastParseFailure = null;
        Integer inputTokens = null;
        Integer outputTokens = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            GenerationResult generation = provider.generate(currentRequest);
            inputTokens = sum(inputTokens, generation.inputTokens());
            outputTokens = sum(outputTokens, generation.outputTokens());
            try {
                T value = parser.apply(generation.text());
                GenerationResult accumulated = new GenerationResult(
                        generation.text(), generation.model(), inputTokens, outputTokens);
                return new ParsedGeneration<>(value, accumulated, attempt);
            } catch (RuntimeException exception) {
                lastParseFailure = exception;
                if (attempt < maxAttempts) currentRequest = currentRequest.retry(responseName);
            }
        }

        throw new StructuredOutputException(
                provider.providerType(), responseName, maxAttempts, lastParseFailure);
    }

    private Integer sum(Integer first, Integer second) {
        if (first == null && second == null) return null;
        return (first == null ? 0 : first) + (second == null ? 0 : second);
    }

    public record ParsedGeneration<T>(T value, GenerationResult generation, int attempts) {
    }
}
