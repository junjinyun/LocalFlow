package com.localflow.domain.agent.domain;

public enum AgentProgressStage {
    PREPARING,
    DECOMPOSING,
    DECOMPOSED,
    DECIDING,
    SELECTING_CONTEXT,
    PLANNING,
    VALIDATING,
    WAITING_APPROVAL,
    APPLYING,
    COMPLETED,
    FAILED
}
