package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.service.AgentProgressMessages;
import org.junit.jupiter.api.Test;

class AgentProgressMessagesTest {
    @Test
    void decisionStartDoesNotClaimAProviderBeforeFallbackIsKnown() {
        assertThat(AgentProgressMessages.decisionStarted())
                .contains("판단 엔진")
                .doesNotContain("Jev", "JEV", "로컬");
    }
}
