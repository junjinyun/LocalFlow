package com.localflow.domain.workspace.dto;

import com.localflow.domain.workspace.domain.SymbolType;
import com.localflow.domain.workspace.entity.CodeSymbol;

public record CodeSymbolResponse(Long id, String name, SymbolType type, int lineNumber) {
    public static CodeSymbolResponse from(CodeSymbol symbol) {
        return new CodeSymbolResponse(symbol.getId(), symbol.getName(), symbol.getType(), symbol.getLineNumber());
    }
}
