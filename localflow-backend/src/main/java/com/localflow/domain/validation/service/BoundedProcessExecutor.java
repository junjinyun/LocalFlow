package com.localflow.domain.validation.service;

import com.localflow.domain.validation.config.ValidationProperties;
import com.localflow.domain.validation.dto.ValidationCommandResult.ProcessExecutionOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class BoundedProcessExecutor {
    private static final Set<String> ENV_ALLOWLIST = Set.of(
            "PATH", "Path", "JAVA_HOME", "SystemRoot", "SYSTEMROOT", "ComSpec", "COMSPEC",
            "PATHEXT", "TEMP", "TMP", "HOME", "USERPROFILE", "APPDATA", "LOCALAPPDATA");

    private final ValidationProperties properties;
    private final Semaphore slots;

    public BoundedProcessExecutor(ValidationProperties properties) {
        this.properties = properties;
        this.slots = new Semaphore(properties.maxConcurrentProcesses(), true);
    }

    public ProcessExecutionOutput execute(List<String> command, Path workingDirectory) {
        if (!slots.tryAcquire()) {
            throw new IllegalStateException("다른 프로젝트 검증이 실행 중입니다.");
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile()).redirectErrorStream(true);
            sanitizeEnvironment(builder.environment(), workingDirectory);
            Process process = builder.start();
            CompletableFuture<CapturedOutput> captured = CompletableFuture.supplyAsync(
                    () -> capture(process, properties.maxOutputChars()));
            boolean finished = process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            CapturedOutput output = captured.get(5, TimeUnit.SECONDS);
            return new ProcessExecutionOutput(finished ? process.exitValue() : null,
                    !finished, output.text(), output.truncated());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("검증 프로세스 실행이 취소되었습니다.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("검증 프로세스를 실행하지 못했습니다: "
                    + exception.getMessage(), exception);
        } finally {
            slots.release();
        }
    }

    private CapturedOutput capture(Process process, int maxChars) {
        StringBuilder captured = new StringBuilder(Math.min(maxChars, 4096));
        AtomicBoolean truncated = new AtomicBoolean(false);
        try (InputStreamReader reader = new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                int remaining = maxChars - captured.length();
                if (remaining > 0) captured.append(buffer, 0, Math.min(remaining, read));
                if (read > remaining) truncated.set(true);
            }
        } catch (IOException exception) {
            if (captured.isEmpty()) captured.append("출력을 읽지 못했습니다: ").append(exception.getMessage());
        }
        if (truncated.get()) {
            String suffix = "\n… 출력 제한 초과";
            int keep = Math.max(0, maxChars - suffix.length());
            if (captured.length() > keep) captured.setLength(keep);
            captured.append(suffix, 0, Math.min(suffix.length(), maxChars - captured.length()));
        }
        return new CapturedOutput(captured.toString(), truncated.get());
    }

    private void sanitizeEnvironment(Map<String, String> environment, Path workingDirectory) {
        Map<String, String> inherited = Map.copyOf(environment);
        environment.clear();
        ENV_ALLOWLIST.forEach(key -> {
            String value = inherited.get(key);
            if (value != null) environment.put(key, value);
        });
        environment.put("CI", "true");
        environment.put("NO_COLOR", "1");
        environment.put("GRADLE_USER_HOME", workingDirectory.resolve(".localflow-gradle").toString());
        environment.put("npm_config_audit", "false");
        environment.put("npm_config_fund", "false");
    }

    private record CapturedOutput(String text, boolean truncated) {
    }
}
