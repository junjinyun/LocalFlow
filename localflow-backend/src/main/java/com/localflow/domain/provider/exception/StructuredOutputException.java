package com.localflow.domain.provider.exception;

import com.localflow.domain.provider.domain.AiProviderType;

public class StructuredOutputException extends RuntimeException {
    public StructuredOutputException(AiProviderType provider, String responseName,
                                     int attempts, Throwable cause) {
        super(provider + "가 " + responseName + " 구조화 응답을 " + attempts
                + "회 생성했지만 형식을 해석하지 못했습니다. 모델과 JSON Schema 설정을 확인해 주세요.",
                cause);
    }
}
