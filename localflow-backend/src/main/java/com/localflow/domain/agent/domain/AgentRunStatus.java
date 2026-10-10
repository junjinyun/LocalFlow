package com.localflow.domain.agent.domain;

public enum AgentRunStatus {
    DRAFT, PENDING, DECIDING, PLANNING, WAITING_APPROVAL,
    RUNNING, VALIDATING, CANCEL_REQUESTED, COMPLETED, FAILED, CANCELLED
}
