package com.localflow.domain.provider.domain;

import com.fasterxml.jackson.databind.JsonNode;

public record GenerationRequest(
        String systemPrompt,
        String userPrompt,
        String model,
        JsonNode responseSchema
) {
    public GenerationRequest(String systemPrompt, String userPrompt, String model) {
        this(systemPrompt, userPrompt, model, null);
    }

    public GenerationRequest withSchemaInstruction(String responseName) {
        if (responseSchema == null || responseSchema.isNull()) return this;
        String instruction = "\n\n반환할 " + responseName + " JSON Schema:\n" + responseSchema;
        return new GenerationRequest(systemPrompt, userPrompt + instruction, model, responseSchema);
    }

    public GenerationRequest retry(String responseName) {
        String instruction = """

                이전 응답은 %s JSON Schema 검증에 실패했습니다.
                설명, 마크다운, 코드 펜스 없이 지정된 스키마에 맞는 JSON 객체 하나만 다시 반환하세요.
                """.formatted(responseName);
        return new GenerationRequest(systemPrompt, userPrompt + instruction, model, responseSchema);
    }
}
