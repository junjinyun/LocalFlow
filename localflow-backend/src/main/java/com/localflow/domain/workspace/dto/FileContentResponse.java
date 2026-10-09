package com.localflow.domain.workspace.dto;

import java.util.List;

public record FileContentResponse(
        ProjectFileResponse file,
        String content,
        List<CodeSymbolResponse> symbols
) {
}
