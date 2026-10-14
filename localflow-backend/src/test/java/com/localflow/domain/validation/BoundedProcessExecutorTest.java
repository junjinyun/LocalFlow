package com.localflow.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.validation.config.ValidationProperties;
import com.localflow.domain.validation.service.BoundedProcessExecutor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedProcessExecutorTest {
    @Test
    void truncatesProcessOutputAtConfiguredLimit() {
        BoundedProcessExecutor executor = new BoundedProcessExecutor(
                new ValidationProperties(true, Duration.ofSeconds(5), 100, 1));

        var result = executor.execute(javaCommand(EmitterProcess.class), Path.of("."));

        assertThat(result.exitCode()).isZero();
        assertThat(result.truncated()).isTrue();
        assertThat(result.output()).hasSizeLessThanOrEqualTo(100);
    }

    @Test
    void terminatesProcessAfterTimeout() {
        BoundedProcessExecutor executor = new BoundedProcessExecutor(
                new ValidationProperties(true, Duration.ofMillis(100), 1000, 1));

        var result = executor.execute(javaCommand(SleeperProcess.class), Path.of("."));

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isNull();
    }

    private List<String> javaCommand(Class<?> mainClass) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String executable = Path.of(System.getProperty("java.home"), "bin",
                windows ? "java.exe" : "java").toString();
        return List.of(executable, "-cp", System.getProperty("java.class.path"), mainClass.getName());
    }

    public static class EmitterProcess {
        public static void main(String[] args) {
            System.out.print("x".repeat(1000));
        }
    }

    public static class SleeperProcess {
        public static void main(String[] args) throws Exception {
            Thread.sleep(5000);
        }
    }
}
