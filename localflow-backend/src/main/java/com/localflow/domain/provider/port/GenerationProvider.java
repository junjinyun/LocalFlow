package com.localflow.domain.provider.port;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GenerationRequest;
import com.localflow.domain.provider.domain.GenerationResult;
import java.util.List;

public interface GenerationProvider {
    AiProviderType providerType();
    boolean available();
    String model();
    default List<String> models() {
        return model() == null || model().isBlank() ? List.of() : List.of(model());
    }
    GenerationResult generate(GenerationRequest request);
}
