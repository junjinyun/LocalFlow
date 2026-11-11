package com.localflow.domain.memory.domain;

public enum MemoryCandidateExtractionTrigger {
    MANUAL, CHAT_MESSAGE, AGENT_RUN;

    public static MemoryCandidateExtractionTrigger from(MemoryCandidateSourceType sourceType) {
        return sourceType == MemoryCandidateSourceType.CHAT_MESSAGE ? CHAT_MESSAGE : AGENT_RUN;
    }
}
