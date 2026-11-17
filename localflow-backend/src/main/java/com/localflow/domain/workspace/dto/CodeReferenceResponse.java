package com.localflow.domain.workspace.dto;

import com.localflow.domain.workspace.domain.CodeReferenceType;
import com.localflow.domain.workspace.entity.CodeReference;

public record CodeReferenceResponse(
        Long id,
        CodeReferenceType type,
        String target,
        int lineNumber
) {
    public static CodeReferenceResponse from(CodeReference reference) {
        return new CodeReferenceResponse(reference.getId(), reference.getType(),
                reference.getTarget(), reference.getLineNumber());
    }
}
