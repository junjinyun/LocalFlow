package com.localflow.domain.agent.service;

final class AgentRunCancelledException extends RuntimeException {
    AgentRunCancelledException() {
        super("에이전트 실행 취소가 요청되었습니다.");
    }
}
