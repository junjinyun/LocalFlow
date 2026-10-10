package com.localflow.domain.provider.service;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.GeminiCliStatus;
import com.localflow.domain.provider.domain.OllamaStatus;
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
        return findAll(false);
    }

    public List<ProviderResponse> findAll(boolean refresh) {
        return List.of(
                decision(AiProviderType.JEV, "TypeSafe Jev (OpenRouter)", "OPENROUTER_API_KEY"),
                generation(AiProviderType.OPENAI, "OpenAI API", "OPENAI_API_KEY", refresh),
                generation(AiProviderType.VERTEX_AI, "Google Cloud Vertex AI", "SERVICE_ACCOUNT_ENV", refresh),
                generation(AiProviderType.OLLAMA, "Ollama", "LOCAL_ENDPOINT", refresh),
                generation(AiProviderType.GEMINI_CLI, "Gemini CLI (Google 계정)", "GOOGLE_ACCOUNT_CLI", refresh)
        );
    }

    private ProviderResponse generation(AiProviderType type, String displayName, String credentialType,
                                        boolean refresh) {
        GenerationProvider provider = generationProviders.get(type);
        if (provider instanceof OllamaGenerationProvider ollamaProvider) {
            OllamaStatus status = ollamaProvider.status(refresh);
            return new ProviderResponse(type, ProviderRole.GENERATION, displayName, credentialType,
                    true, status.configured(), status.reachable(), status.modelInstalled(),
                    status.available(), status.message(), provider.model(), status.models());
        }
        if (provider instanceof GeminiCliGenerationProvider geminiCliProvider) {
            GeminiCliStatus status = geminiCliProvider.status(refresh);
            return new ProviderResponse(type, ProviderRole.GENERATION, displayName, credentialType,
                    true, status.configured(), status.reachable(), null,
                    status.available(), status.message(), provider.model(), status.models());
        }
        boolean available = provider != null && provider.available();
        return new ProviderResponse(type, ProviderRole.GENERATION, displayName, credentialType,
                provider != null, available, null, null, available,
                available ? "사용할 수 있습니다." : "환경 설정이 필요합니다.",
                provider == null ? null : provider.model(),
                provider == null ? List.of() : provider.models());
    }

    private ProviderResponse decision(AiProviderType type, String displayName, String credentialType) {
        DecisionProvider provider = decisionProviders.get(type);
        boolean available = provider != null && provider.available();
        return new ProviderResponse(type, ProviderRole.DECISION, displayName, credentialType,
                provider != null, available, null, null, available,
                available ? "사용할 수 있습니다." : "환경 설정이 필요합니다.",
                provider == null ? null : provider.model(), List.of());
    }
}
