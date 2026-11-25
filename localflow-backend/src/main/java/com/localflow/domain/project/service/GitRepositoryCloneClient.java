package com.localflow.domain.project.service;

import com.localflow.global.error.CustomException;
import com.localflow.global.error.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GitRepositoryCloneClient {
    private static final Duration CLONE_TIMEOUT = Duration.ofMinutes(2);

    public void cloneShallow(GitHubRepositoryReference reference, Path sourceRoot) {
        try {
            Files.createDirectories(sourceRoot.getParent());
            List<String> command = new ArrayList<>(List.of(
                    "git", "clone", "--depth", "1", "--single-branch"));
            if (reference.branch() != null) {
                command.add("--branch");
                command.add(reference.branch());
            }
            command.add(reference.cloneUrl());
            command.add(sourceRoot.toString());
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean completed = process.waitFor(CLONE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
            }
            if (process.exitValue() != 0) throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        } catch (IOException exception) {
            throw new CustomException(ErrorCode.GITHUB_IMPORT_UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.GITHUB_IMPORT_FAILED);
        }
    }
}
