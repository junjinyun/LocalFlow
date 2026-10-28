package com.localflow.domain.provider.exception;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;

public class StructuredOutputException extends ProviderCallException {
    private final int attempts;

    public StructuredOutputException(AiProviderType provider, String responseName,
                                     int attempts, Throwable cause) {
        super(provider, ProviderErrorCode.INVALID_RESPONSE,
                provider + "가 " + responseName + " 응답을 올바른 형식으로 반환하지 못했습니다.",
                false, "다른 모델을 선택하거나 프롬프트 범위를 줄여 다시 시도해 주세요.",
                provider + " structured output parsing failed after " + attempts + " attempts",
                cause);
        this.attempts = attempts;
    }

    public int attempts() { return attempts; }
}
