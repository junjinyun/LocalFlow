package com.localflow.domain.provider.exception;

import com.localflow.domain.provider.domain.AiProviderType;

public class ProviderCallException extends RuntimeException {
    private final AiProviderType providerType;

    public ProviderCallException(AiProviderType providerType, String message) {
        super(message);
        this.providerType = providerType;
    }

    public ProviderCallException(AiProviderType providerType, String message, Throwable cause) {
        super(message, cause);
        this.providerType = providerType;
    }

    public AiProviderType providerType() { return providerType; }
}
