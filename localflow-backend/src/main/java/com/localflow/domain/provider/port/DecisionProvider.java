package com.localflow.domain.provider.port;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.DecisionRequest;
import com.localflow.domain.provider.domain.DecisionResult;

public interface DecisionProvider {
    AiProviderType providerType();
    boolean available();
    String model();
    DecisionResult decide(DecisionRequest request);
}
