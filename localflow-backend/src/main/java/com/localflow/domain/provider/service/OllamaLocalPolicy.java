package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class OllamaLocalPolicy {
    private final AiProviderProperties.Ollama properties;

    public OllamaLocalPolicy(AiProviderProperties properties) {
        this.properties = properties.ollama();
    }

    public void validate(String model) {
        if (properties == null || !isLoopback(properties.baseUrl()) || isCloudModel(model)) {
            throw new CustomException(ErrorCode.REMOTE_OLLAMA_DENIED);
        }
    }

    boolean isLoopback(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.strip());
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) return false;
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return false;
            String host = uri.getHost();
            if (host == null) return false;
            host = host.replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.equals("::1")
                    || host.equals("0:0:0:0:0:0:0:1")) return true;
            String[] parts = host.split("\\.");
            if (parts.length != 4 || !"127".equals(parts[0])) return false;
            for (String part : parts) {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) return false;
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    boolean isCloudModel(String model) {
        if (model == null || model.isBlank()) return false;
        String normalized = model.strip().toLowerCase(Locale.ROOT);
        int tagStart = normalized.lastIndexOf(':');
        String tag = tagStart < 0 ? normalized : normalized.substring(tagStart + 1);
        return tag.equals("cloud") || tag.startsWith("cloud-") || tag.endsWith("-cloud")
                || tag.contains("-cloud-");
    }
}
