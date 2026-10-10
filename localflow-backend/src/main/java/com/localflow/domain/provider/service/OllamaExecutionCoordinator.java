package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class OllamaExecutionCoordinator {
    private final Duration queueTimeout;
    private final Duration requestTimeout;
    private final Semaphore permits;
    private final AtomicInteger waiting = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicBoolean warmedUp = new AtomicBoolean();

    public OllamaExecutionCoordinator(AiProviderProperties properties) {
        AiProviderProperties.Ollama ollama = properties.ollama();
        this.queueTimeout = ollama == null ? Duration.ofMinutes(15) : ollama.queueTimeout();
        this.requestTimeout = ollama == null ? Duration.ofMinutes(10) : ollama.requestTimeout();
        this.permits = new Semaphore(ollama == null ? 1 : ollama.maxConcurrentRequests(), true);
    }

    public <T> T execute(Supplier<T> operation) {
        boolean acquired = false;
        waiting.incrementAndGet();
        try {
            acquired = permits.tryAcquire(queueTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ProviderCallException(AiProviderType.OLLAMA,
                        "Ollama 실행 대기 시간이 " + describe(queueTimeout)
                                + "을 초과했습니다. 다른 작업이 끝난 뒤 다시 시도해 주세요.");
            }
            waiting.decrementAndGet();
            active.incrementAndGet();
            T result = operation.get();
            warmedUp.set(true);
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(AiProviderType.OLLAMA,
                    "Ollama 실행 대기 중 요청이 중단되었습니다.", exception);
        } finally {
            if (!acquired) waiting.updateAndGet(value -> Math.max(0, value - 1));
            if (acquired) {
                active.decrementAndGet();
                permits.release();
            }
        }
    }

    public ExecutionStatus status() {
        return new ExecutionStatus(waiting.get(), active.get(), warmedUp.get(), requestTimeout);
    }

    private String describe(Duration duration) {
        long minutes = duration.toMinutes();
        return minutes > 0 ? minutes + "분" : duration.toSeconds() + "초";
    }

    public record ExecutionStatus(int waiting, int active, boolean warmedUp,
                                  Duration requestTimeout) {
    }
}
