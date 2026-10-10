package com.localflow.domain.provider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localflow.domain.provider.config.AiProviderProperties;
import com.localflow.domain.provider.exception.ProviderCallException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OllamaExecutionCoordinatorTest {
    @Test
    void serializesRepeatedRequestsOnSingleLocalSlot() throws Exception {
        OllamaExecutionCoordinator coordinator = coordinator(Duration.ofSeconds(2));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        try {
            Future<String> first = executor.submit(() -> coordinator.execute(() -> {
                enter(active, maxActive);
                firstEntered.countDown();
                await(releaseFirst);
                active.decrementAndGet();
                return "first";
            }));
            assertThat(firstEntered.await(1, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> coordinator.execute(() -> {
                enter(active, maxActive);
                active.decrementAndGet();
                return "second";
            }));

            awaitWaiting(coordinator);
            assertThat(coordinator.status().active()).isEqualTo(1);
            assertThat(coordinator.status().waiting()).isEqualTo(1);
            releaseFirst.countDown();

            assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
            assertThat(maxActive).hasValue(1);
            assertThat(coordinator.status().warmedUp()).isTrue();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failsWithReadableMessageWhenQueueWaitExpires() throws Exception {
        OllamaExecutionCoordinator coordinator = coordinator(Duration.ofMillis(80));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> coordinator.execute(() -> {
                entered.countDown();
                await(release);
                return null;
            }));
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> coordinator.execute(() -> "second"))
                    .isInstanceOf(ProviderCallException.class)
                    .hasMessageContaining("Ollama 실행 대기 시간")
                    .hasMessageContaining("다시 시도");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private OllamaExecutionCoordinator coordinator(Duration queueTimeout) {
        AiProviderProperties properties = new AiProviderProperties(32_000, 20, null, null,
                new AiProviderProperties.Ollama(true, "http://localhost:11434", "qwen2.5-coder:3b",
                        Duration.ofSeconds(5), Duration.ofSeconds(2), 0, 42, false,
                        4_096, 16_384, "10m", Duration.ofMinutes(10), queueTimeout, 1, 1),
                null);
        return new OllamaExecutionCoordinator(properties);
    }

    private void enter(AtomicInteger active, AtomicInteger maxActive) {
        int current = active.incrementAndGet();
        maxActive.updateAndGet(previous -> Math.max(previous, current));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void awaitWaiting(OllamaExecutionCoordinator coordinator) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (coordinator.status().waiting() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
    }
}
