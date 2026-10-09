package com.localflow.domain.provider.dto;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderRole;
import java.util.List;

public record ProviderResponse(
        AiProviderType type,
        ProviderRole role,
        String displayName,
        String credentialType,
        boolean implemented,
        boolean configured,
        Boolean reachable,
        Boolean modelInstalled,
        boolean available,
        String statusMessage,
        String model,
        List<String> models
) {
}
