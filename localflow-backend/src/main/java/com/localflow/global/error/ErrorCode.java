package com.localflow.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."),
    PROJECT_SETTINGS_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 설정을 찾을 수 없습니다."),
    PROJECT_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 파일을 찾을 수 없습니다."),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅 메시지를 찾을 수 없습니다."),
    MEMORY_NOT_FOUND(HttpStatus.NOT_FOUND, "프로젝트 기억을 찾을 수 없습니다."),
    AGENT_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "에이전트 실행 기록을 찾을 수 없습니다."),
    INVALID_FILE_PATH(HttpStatus.BAD_REQUEST, "허용되지 않는 파일 경로입니다."),
    UNSUPPORTED_FILE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_LIMIT_EXCEEDED(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 파일 제한을 초과했습니다."),
    MULTIPART_UPLOAD_FAILED(HttpStatus.BAD_REQUEST,
            "업로드 요청을 해석하지 못했습니다. 파일 수와 전체 용량을 확인해 주세요."),
    FILE_OPERATION_DENIED(HttpStatus.FORBIDDEN, "프로젝트 설정에서 허용하지 않은 파일 작업입니다."),
    INVALID_RUN_STATUS(HttpStatus.CONFLICT, "현재 상태에서는 요청한 실행 작업을 처리할 수 없습니다."),
    PROVIDER_NOT_CONFIGURED(HttpStatus.BAD_REQUEST, "선택한 AI 제공자의 환경 변수가 설정되지 않았습니다."),
    AI_MODEL_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "선택한 AI 모델은 현재 제공자에서 허용되지 않습니다."),
    EXTERNAL_PROVIDER_DENIED(HttpStatus.FORBIDDEN, "로컬 전용 개인정보 설정에서는 외부 AI를 사용할 수 없습니다."),
    REMOTE_OLLAMA_DENIED(HttpStatus.FORBIDDEN,
            "로컬 전용 개인정보 설정에서는 loopback Ollama 주소와 로컬 모델만 사용할 수 있습니다."),
    INVALID_AI_PLAN(HttpStatus.BAD_GATEWAY, "AI가 반환한 파일 작업 계획 형식이 올바르지 않습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() { return status; }
    public String message() { return message; }
}
