package com.localflow.domain.provider.service;

import java.util.regex.Pattern;

public final class SensitiveDataMasker {
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[a-z0-9._~+/-]+=*");
    private static final Pattern OPENAI_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\\\"?(?:api[_-]?key|access[_-]?token|private[_-]?key|client[_-]?secret)\\\"?\\s*[:=]\\s*\\\"?)[^\\\"\\s,}]+");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----");

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isBlank()) return value;
        String masked = PRIVATE_KEY.matcher(value).replaceAll("[MASKED_PRIVATE_KEY]");
        masked = BEARER.matcher(masked).replaceAll("Bearer [MASKED]");
        masked = OPENAI_KEY.matcher(masked).replaceAll("[MASKED_API_KEY]");
        return JSON_SECRET.matcher(masked).replaceAll("$1[MASKED]");
    }
}
