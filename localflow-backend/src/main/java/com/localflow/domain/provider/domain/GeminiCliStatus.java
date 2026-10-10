package com.localflow.domain.provider.domain;

import java.util.List;

public record GeminiCliStatus(
        boolean configured,
        boolean reachable,
        boolean available,
        String message,
        String version,
        List<String> models
) {
    public GeminiCliStatus {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
