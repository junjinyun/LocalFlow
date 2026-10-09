package com.localflow.domain.provider.service;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderRole;
import com.localflow.domain.provider.dto.ProviderResponse;
import com.localflow.domain.provider.port.DecisionProvider;
import com.localflow.domain.provider.port.GenerationProvider;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProviderCatalogService {
    private final Map<AiProviderType, GenerationProvider> generationProviders;
    private final Map<AiProviderType, DecisionProvider> decisionProviders;

    public ProviderCatalogService(List<GenerationProvider> generationProviders,
                                  List<DecisionProvider> decisionProviders) {
        this.generationProviders = new EnumMap<>(AiProviderType.class);
        generationProviders.forEach(provider -> this.generationProviders.put(provider.providerType(), provider));
        this.decisionProviders = new EnumMap<>(AiProviderType.class);
        decisionProviders.forEach(provider -> this.decisionProviders.put(provider.providerType(), provider));
    }

    public List<ProviderResponse> findAll() {
        return List.of(
                decision(AiProviderType.JEV, "TypeSafe Jev (OpenRouter)", "OPENROUTER_API_KEY"),
                generation(AiProviderType.OPENAI, "OpenAI API", "OPENAI_API_KEY"),
                generation(AiProviderType.VERTEX_AI, "Google Cloud Vertex AI", "SERVICE_ACCOUNT_ENV"),
                generation(AiProviderType.OLLAMA, "Ollama", "LOCAL_ENDPOINT")
        );
    }

    private ProviderResponse generation(AiProviderType type, String displayName, String credentialType) {
        GenerationProvider provider = generationProviders.get(type);
        return new ProviderResponse(type, ProviderRole.GENERATION, displayName, credentialType,
                provider != null, provider != null && provider.available(),
                provider == null ? null : provider.model(),
                provider == null ? List.of() : provider.models());
    }

    private ProviderResponse decision(AiProviderType type, String displayName, String credentialType) {
        DecisionProvider provider = decisionProviders.get(type);
        return new ProviderResponse(type, ProviderRole.DECISION, displayName, credentialType,
                provider != null, provider != null && provider.available(),
                provider == null ? null : provider.model(), List.of());
    }
}
