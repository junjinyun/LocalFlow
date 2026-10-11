package com.localflow.domain.provider.service;

import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.domain.ProviderErrorCode;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;

public class ProviderErrorMapper {
    public ProviderCallException fromHttp(AiProviderType provider, int status, String responseBody) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        ProviderErrorCode code;
        boolean retryable;
        String message;
        String action;
        if (status == 401) {
            code = ProviderErrorCode.AUTHENTICATION_FAILED;
            retryable = false;
            message = providerName(provider) + " 인증에 실패했습니다.";
            action = "API 키 또는 로그인 정보를 다시 확인해 주세요.";
        } else if (status == 403) {
            code = ProviderErrorCode.PERMISSION_DENIED;
            retryable = false;
            message = providerName(provider) + " 호출 권한이 없습니다.";
            action = "계정 권한, API 활성화 상태와 서비스 계정 역할을 확인해 주세요.";
        } else if (status == 429 && containsAny(body, "quota", "billing", "credit", "insufficient")) {
            code = ProviderErrorCode.QUOTA_EXCEEDED;
            retryable = false;
            message = providerName(provider) + " 사용량 또는 결제 한도를 초과했습니다.";
            action = "제공자 콘솔에서 할당량과 결제 상태를 확인해 주세요.";
        } else if (status == 429) {
            code = ProviderErrorCode.RATE_LIMITED;
            retryable = true;
            message = providerName(provider) + " 요청 제한에 도달했습니다.";
            action = "잠시 기다린 뒤 다시 시도해 주세요.";
        } else if (status == 408 || status == 504) {
            code = ProviderErrorCode.TIMEOUT;
            retryable = true;
            message = providerName(provider) + " 응답 시간이 초과되었습니다.";
            action = "잠시 후 다시 시도하거나 더 작은 요청으로 나눠 주세요.";
        } else if (status == 404 || (status == 400 && containsAny(body, "model", "not found"))) {
            code = ProviderErrorCode.MODEL_NOT_SUPPORTED;
            retryable = false;
            message = "선택한 AI 모델을 사용할 수 없습니다.";
            action = "제공자 설정에서 지원되는 모델을 선택해 주세요.";
        } else if (status >= 500) {
            code = ProviderErrorCode.SERVICE_UNAVAILABLE;
            retryable = true;
            message = providerName(provider) + " 서비스가 일시적으로 응답하지 않습니다.";
            action = "잠시 후 다시 시도해 주세요.";
        } else {
            code = ProviderErrorCode.REQUEST_REJECTED;
            retryable = false;
            message = providerName(provider) + "가 요청을 처리하지 못했습니다.";
            action = "모델 설정과 요청 크기를 확인해 주세요.";
        }
        return new ProviderCallException(provider, code, message, retryable, action,
                "HTTP " + status + " returned by " + provider);
    }

    public ProviderCallException fromThrowable(AiProviderType provider, Throwable throwable) {
        Throwable root = rootCause(throwable);
        String rootMessage = root.getMessage() == null ? "" : root.getMessage().toLowerCase(Locale.ROOT);
        if (root instanceof HttpTimeoutException || containsAny(rootMessage, "timeout", "time out", "시간을 초과")) {
            return new ProviderCallException(provider, ProviderErrorCode.TIMEOUT,
                    providerName(provider) + " 응답 시간이 초과되었습니다.", true,
                    "잠시 후 다시 시도하거나 더 작은 요청으로 나눠 주세요.",
                    diagnostic(root), throwable);
        }
        if (root instanceof ConnectException || root instanceof java.io.IOException) {
            return new ProviderCallException(provider, ProviderErrorCode.NETWORK_ERROR,
                    providerName(provider) + " 서버에 연결하지 못했습니다.", true,
                    "네트워크 연결과 제공자 서버 주소를 확인한 뒤 다시 시도해 주세요.",
                    diagnostic(root), throwable);
        }
        if (root instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return new ProviderCallException(provider, ProviderErrorCode.TIMEOUT,
                    providerName(provider) + " 요청이 중단되었습니다.", true,
                    "잠시 후 다시 시도해 주세요.", diagnostic(root), throwable);
        }
        return new ProviderCallException(provider, ProviderErrorCode.UNKNOWN,
                providerName(provider) + " 호출 중 알 수 없는 오류가 발생했습니다.", false,
                "설정과 서버 로그를 확인해 주세요.", diagnostic(root), throwable);
    }

    public ProviderCallException invalidResponse(AiProviderType provider, String diagnostic) {
        return new ProviderCallException(provider, ProviderErrorCode.INVALID_RESPONSE,
                providerName(provider) + " 응답 형식을 해석하지 못했습니다.", true,
                "같은 요청을 다시 시도하거나 다른 모델을 선택해 주세요.",
                SensitiveDataMasker.mask(diagnostic));
    }

    public ProviderCallException fromCli(AiProviderType provider, int exitCode, String output) {
        String value = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (containsAny(value, "unauthorized", "unauthenticated", "login", "credential")) {
            return new ProviderCallException(provider, ProviderErrorCode.AUTHENTICATION_FAILED,
                    providerName(provider) + " 로그인 또는 인증에 실패했습니다.", false,
                    "CLI에서 계정 로그인을 다시 진행해 주세요.",
                    "CLI exited with code " + exitCode);
        }
        if (containsAny(value, "quota", "billing", "credit", "insufficient", "resource_exhausted")) {
            return new ProviderCallException(provider, ProviderErrorCode.QUOTA_EXCEEDED,
                    providerName(provider) + " 사용량 또는 결제 한도를 초과했습니다.", false,
                    "계정의 사용량과 결제 상태를 확인해 주세요.",
                    "CLI exited with code " + exitCode);
        }
        if (containsAny(value, "rate limit", "too many requests", "429")) {
            return new ProviderCallException(provider, ProviderErrorCode.RATE_LIMITED,
                    providerName(provider) + " 요청 제한에 도달했습니다.", true,
                    "잠시 기다린 뒤 다시 시도해 주세요.",
                    "CLI exited with code " + exitCode);
        }
        if (containsAny(value, "model not found", "unsupported model", "unknown model")) {
            return new ProviderCallException(provider, ProviderErrorCode.MODEL_NOT_SUPPORTED,
                    "선택한 AI 모델을 사용할 수 없습니다.", false,
                    "설치 또는 접근 가능한 모델을 선택해 주세요.",
                    "CLI exited with code " + exitCode);
        }
        return new ProviderCallException(provider, ProviderErrorCode.REQUEST_REJECTED,
                providerName(provider) + "가 요청을 처리하지 못했습니다.", false,
                "CLI 로그인 상태와 모델 설정을 확인해 주세요.",
                "CLI exited with code " + exitCode);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private String diagnostic(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null ? "" : ": " + SensitiveDataMasker.mask(message));
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String providerName(AiProviderType provider) {
        return switch (provider) {
            case OPENAI -> "OpenAI";
            case VERTEX_AI -> "Google Cloud Vertex AI";
            case OLLAMA -> "Ollama";
            case JEV -> "OpenRouter/JEV";
            case GEMINI_CLI -> "Gemini CLI";
        };
    }
}
