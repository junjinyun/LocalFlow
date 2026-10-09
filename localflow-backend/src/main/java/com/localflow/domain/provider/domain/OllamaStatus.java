package com.localflow.domain.provider.domain;

import java.util.List;

public record OllamaStatus(
        boolean configured,
        boolean reachable,
        boolean modelInstalled,
        boolean available,
        String message,
        List<String> models
) {
    public OllamaStatus {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
