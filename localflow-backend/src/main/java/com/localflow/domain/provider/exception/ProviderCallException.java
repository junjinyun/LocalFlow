package com.localflow.domain.provider.exception;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;

public class ProviderCallException extends RuntimeException {
    private final AiProviderType providerType;
    private final ProviderErrorCode errorCode;
    private final boolean retryable;
    private final String recommendedAction;
    private final String diagnosticMessage;

    public ProviderCallException(AiProviderType providerType, String message) {
        this(providerType, ProviderErrorCode.UNKNOWN, message, false,
                "설정을 확인한 뒤 다시 시도해 주세요.", message, null);
    }

    public ProviderCallException(AiProviderType providerType, String message, Throwable cause) {
        this(providerType, ProviderErrorCode.UNKNOWN, message, false,
                "설정과 서버 로그를 확인해 주세요.", message, cause);
    }

    public ProviderCallException(AiProviderType providerType, ProviderErrorCode errorCode,
                                 String userMessage, boolean retryable,
                                 String recommendedAction, String diagnosticMessage) {
        this(providerType, errorCode, userMessage, retryable, recommendedAction,
                diagnosticMessage, null);
    }

    public ProviderCallException(AiProviderType providerType, ProviderErrorCode errorCode,
                                 String userMessage, boolean retryable,
                                 String recommendedAction, String diagnosticMessage,
                                 Throwable cause) {
        super(userMessage, cause);
        this.providerType = providerType;
        this.errorCode = errorCode == null ? ProviderErrorCode.UNKNOWN : errorCode;
        this.retryable = retryable;
        this.recommendedAction = recommendedAction;
        this.diagnosticMessage = diagnosticMessage;
    }

    public AiProviderType providerType() { return providerType; }
    public ProviderErrorCode errorCode() { return errorCode; }
    public boolean retryable() { return retryable; }
    public String recommendedAction() { return recommendedAction; }
    public String diagnosticMessage() { return diagnosticMessage; }
    public String userMessage() {
        return recommendedAction == null || recommendedAction.isBlank()
                ? getMessage() : getMessage() + " " + recommendedAction;
    }

    public static ProviderCallException notConfigured(AiProviderType providerType, String message) {
        return new ProviderCallException(providerType, ProviderErrorCode.NOT_CONFIGURED,
                message, false, "환경 변수와 제공자 설정을 확인해 주세요.",
                providerType + " is not configured");
    }

    public static ProviderCallException invalidResponse(AiProviderType providerType, String message) {
        return new ProviderCallException(providerType, ProviderErrorCode.INVALID_RESPONSE,
                message, true, "같은 요청을 다시 시도하거나 다른 모델을 선택해 주세요.",
                providerType + " returned an invalid response");
    }

    public static ProviderCallException timeout(AiProviderType providerType, String message) {
        return new ProviderCallException(providerType, ProviderErrorCode.TIMEOUT,
                message, true, "잠시 후 다시 시도해 주세요.",
                providerType + " request timed out");
    }

    public static ProviderCallException timeout(AiProviderType providerType, String message,
                                                Throwable cause) {
        return new ProviderCallException(providerType, ProviderErrorCode.TIMEOUT,
                message, true, "잠시 후 다시 시도해 주세요.",
                providerType + " request timed out", cause);
    }
}
