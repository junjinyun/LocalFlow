package com.localflow.domain.agent.service;

public final class AgentProgressMessages {
    private AgentProgressMessages() {
    }

    public static String decisionStarted() {
        return "판단 엔진이 관련 태그, 대상 범위와 작업 위험도를 확인하는 중입니다.";
    }
}
