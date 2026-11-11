package com.localflow.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.localflow.domain.agent.domain.AgentResponseContract;
import org.junit.jupiter.api.Test;

class AgentResponseContractTest {
    @Test
    void extractsKoreanSentenceAndItemLimits() {
        AgentResponseContract contract = AgentResponseContract.from(
                "실제 파일만으로 최대 5문장, 근거는 3개 이내로 요약하라.");

        assertThat(contract.maxSentences()).isEqualTo(5);
        assertThat(contract.maxItems()).isEqualTo(3);
        assertThat(contract.instruction()).contains(
                "response 문장과 findings 각 항목", "필수 종료 문구", "최대 3개 항목");
    }

    @Test
    void extractsNativeKoreanAndEnglishSentenceLimits() {
        assertThat(AgentResponseContract.from("세 문장으로 설명해줘").maxSentences()).isEqualTo(3);
        assertThat(AgentResponseContract.from("Answer within 4 sentences").maxSentences()).isEqualTo(4);
    }

    @Test
    void ignoresUnboundedFileCount() {
        AgentResponseContract contract = AgentResponseContract.from("관련 파일 3개를 확인해줘");

        assertThat(contract.constrained()).isFalse();
    }
}
