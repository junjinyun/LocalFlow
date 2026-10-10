package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.domain.AiProviderType;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class GeminiCliExecutionCoordinator {
    private final AiProviderProperties.GeminiCli properties;
    private final Semaphore permits;
    private final AtomicInteger waiting = new AtomicInteger();

    public GeminiCliExecutionCoordinator(AiProviderProperties properties) {
        this.properties = properties.geminiCli();
        int concurrency = this.properties == null ? 1 : this.properties.maxConcurrentRequests();
        this.permits = new Semaphore(concurrency, true);
    }

    public <T> T execute(CheckedSupplier<T> supplier) {
        boolean acquired = false;
        waiting.incrementAndGet();
        try {
            long waitMillis = properties == null ? TimeUnit.MINUTES.toMillis(15)
                    : properties.queueTimeout().toMillis();
            acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ProviderCallException(AiProviderType.GEMINI_CLI,
                        "다른 Gemini CLI 작업이 끝나기를 기다리다 제한 시간을 초과했습니다.");
            }
            return supplier.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(AiProviderType.GEMINI_CLI,
                    "Gemini CLI 작업 대기가 중단되었습니다.", exception);
        } catch (ProviderCallException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ProviderCallException(AiProviderType.GEMINI_CLI,
                    "Gemini CLI 실행에 실패했습니다: " + safeMessage(exception), exception);
        } finally {
            waiting.decrementAndGet();
            if (acquired) permits.release();
        }
    }

    public ExecutionStatus status() {
        int max = properties == null ? 1 : properties.maxConcurrentRequests();
        return new ExecutionStatus(max - permits.availablePermits(), waiting.get(), max);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    public record ExecutionStatus(int active, int waiting, int maxConcurrent) {
    }
}
