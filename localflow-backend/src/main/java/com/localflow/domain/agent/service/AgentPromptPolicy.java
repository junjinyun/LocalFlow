package com.localflow.domain.agent.service;

import com.localflow.domain.agent.domain.AgentResponseContract;

public final class AgentPromptPolicy {
    private AgentPromptPolicy() {
    }

    public static String reinforceFinalConstraints(String composedPrompt, String originalRequest) {
        AgentResponseContract contract = AgentResponseContract.from(originalRequest);
        return composedPrompt + """


                ## 최종 응답 직전 필수 확인
                - 아래 원본 요청의 문장 수, 항목 수, 출력 형식과 필수 포함 문구를 최종 결과에 그대로 적용한다.
                - 프로젝트 맥락의 '관련 프로젝트 기억'에 명시된 출력 규칙과 필수 문구를 최종 결과에 반영한다.
                - 조회·분석 전용 요청의 파일 생성, 수정, 이동, 삭제 금지를 다시 확인한다.
                - 작업 분해 지시보다 원본 요청과 프로젝트 기억의 최종 응답 제약을 우선한다.

                ## 응답 형식 계약
                """ + contract.instruction() + """

                원본 사용자 요청:
                """ + originalRequest;
    }
}
