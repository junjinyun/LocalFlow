package com.localflow.domain.memory.dto;

import com.localflow.domain.memory.domain.MemoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import java.util.Set;

public record ProjectMemoryCreateRequest(
        @NotNull MemoryType type,
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 10000) String content,
        Boolean globalScope,
        @Size(max = 20) Set<@NotBlank @Size(max = 40)
                @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String> scopeTags,
        @Size(max = 20) Set<@NotBlank @Size(max = 300) String> scopeFilePatterns,
        @Size(max = 10) Set<@NotBlank @Size(max = 40) String> scopeTaskTypes
) {
    public ProjectMemoryCreateRequest {
        globalScope = globalScope == null || globalScope;
        scopeTags = scopeTags == null ? Set.of() : Set.copyOf(scopeTags);
        scopeFilePatterns = scopeFilePatterns == null ? Set.of() : Set.copyOf(scopeFilePatterns);
        scopeTaskTypes = scopeTaskTypes == null ? Set.of() : Set.copyOf(scopeTaskTypes);
    }

    public ProjectMemoryCreateRequest(MemoryType type, String title, String content) {
        this(type, title, content, true, Set.of(), Set.of(), Set.of());
    }
}
