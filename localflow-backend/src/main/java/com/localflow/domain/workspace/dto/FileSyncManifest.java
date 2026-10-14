package com.localflow.domain.workspace.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FileSyncManifest(
        @NotEmpty
        @Size(max = 5000)
        List<@Valid @NotBlank @Size(max = 1000) String> relativePaths,
        boolean scopedSync,
        @Size(max = 100)
        List<@Valid @NotBlank @Size(max = 1000) String> scopePaths
) {
    public FileSyncManifest {
        scopePaths = scopePaths == null ? List.of() : List.copyOf(scopePaths);
    }
}
