package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentApplyResult;

public class AgentFileApplyException extends RuntimeException {
    private final AgentApplyResult result;

    public AgentFileApplyException(String message, Throwable cause, AgentApplyResult result) {
        super(message, cause);
        this.result = result;
    }

    public AgentApplyResult result() {
        return result;
    }
}
