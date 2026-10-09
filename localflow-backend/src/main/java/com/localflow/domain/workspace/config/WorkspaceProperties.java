package com.localflow.domain.workspace.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localflow.workspace")
public record WorkspaceProperties(
        Path root,
        long maxProjectBytes,
        long maxFileBytes,
        int maxFiles,
        List<String> ignoredDirectories
) {
    public WorkspaceProperties {
        root = root == null ? Path.of("./workspace") : root;
        maxProjectBytes = maxProjectBytes <= 0 ? 262_144_000L : maxProjectBytes;
        maxFileBytes = maxFileBytes <= 0 ? 31_457_280L : maxFileBytes;
        maxFiles = maxFiles <= 0 ? 5_000 : maxFiles;
        ignoredDirectories = ignoredDirectories == null || ignoredDirectories.isEmpty()
                ? List.of(".git", ".gradle", ".idea", ".venv", "build", "coverage",
                "dist", ".next", ".nuxt", ".turbo", ".cache", "node_modules", "out",
                "target", "vendor", "venv")
                : List.copyOf(ignoredDirectories);
    }
}
