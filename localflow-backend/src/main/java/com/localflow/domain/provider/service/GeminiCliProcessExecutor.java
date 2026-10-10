package com.localflow.domain.provider.service;

import com.localflow.domain.provider.config.AiProviderProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class GeminiCliProcessExecutor {
    private final AiProviderProperties.GeminiCli properties;
    private final Path workingDirectory;

    public GeminiCliProcessExecutor(AiProviderProperties properties) {
        this.properties = properties.geminiCli();
        this.workingDirectory = Path.of(System.getProperty("java.io.tmpdir"), "localflow-gemini-cli");
    }

    public ProcessResult execute(List<String> arguments, String input, Duration timeout)
            throws IOException, InterruptedException {
        if (properties == null) throw new IOException("Gemini CLI 설정이 없습니다.");
        Files.createDirectories(workingDirectory);
        ProcessBuilder builder = new ProcessBuilder(command(arguments));
        builder.directory(workingDirectory.toFile());
        sanitizeEnvironment(builder);
        Process process = builder.start();
        ExecutorService readers = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                    () -> read(process.getInputStream()), readers);
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                    () -> read(process.getErrorStream()), readers);
            if (input != null) process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                throw new IOException("Gemini CLI 실행 제한 시간을 초과했습니다.");
            }
            return new ProcessResult(process.exitValue(), await(stdout), await(stderr));
        } finally {
            readers.shutdownNow();
        }
    }

    private List<String> command(List<String> arguments) {
        if (!isWindows()) {
            List<String> command = new ArrayList<>();
            command.add(properties.command());
            command.addAll(arguments);
            return command;
        }
        List<String> tokens = new ArrayList<>();
        tokens.add(properties.command());
        tokens.addAll(arguments);
        String commandLine = tokens.stream().map(this::quoteForWindows).reduce((left, right) -> left + " " + right)
                .orElseThrow();
        return List.of("cmd.exe", "/d", "/s", "/c", commandLine);
    }

    private String quoteForWindows(String value) {
        if (value.matches("[A-Za-z0-9_./:\\-]+")) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void sanitizeEnvironment(ProcessBuilder builder) {
        builder.environment().keySet().removeIf(key -> {
            String normalized = key.toUpperCase(Locale.ROOT);
            return normalized.endsWith("_API_KEY")
                    || normalized.endsWith("_ACCESS_TOKEN")
                    || normalized.equals("GH_TOKEN")
                    || normalized.equals("GITHUB_TOKEN")
                    || normalized.equals("VERTEX_AI_SERVICE_ACCOUNT_BASE64")
                    || normalized.equals("GOOGLE_APPLICATION_CREDENTIALS");
        });
        builder.environment().put("NO_COLOR", "true");
    }

    private String read(java.io.InputStream input) {
        try (input) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String await(CompletableFuture<String> output) throws IOException, InterruptedException {
        try {
            return output.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            throw new IOException("Gemini CLI 출력을 읽지 못했습니다.", exception.getCause());
        } catch (TimeoutException exception) {
            throw new IOException("Gemini CLI 출력 처리가 끝나지 않았습니다.", exception);
        }
    }

    public record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
