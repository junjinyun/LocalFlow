# LocalFlow

LocalFlow는 개발자가 사용 중인 AI 도구의 대화·에이전트 한도에 도달했거나, 에이전트 기능이 없는 AI API를 활용해야 할 때 사용할 수 있는 웹 기반 로컬 프로젝트 작업 보조 서비스입니다.

사용자가 업로드한 프로젝트 사본을 분석해 파일 구조, 코드 심볼과 다중 태그를 인덱싱하고, 선택한 생성 AI와 Jev 의사결정 모델을 조합해 프롬프트 단위의 파일 변경 계획을 생성합니다. 원본 PC 디렉터리에 직접 접근하지 않으며 변경 전후 내용을 확인하고 승인한 결과만 업로드 사본에 적용합니다.

## 핵심 실행 흐름

```text
프로젝트 디렉터리 업로드·동기화
→ 파일 구조·심볼·다중 태그 분석
→ 사용자가 선택한 생성 AI가 요청을 작업 단위로 분해
→ Jev가 작업 유효성·관련 태그·위험도·승인 필요 여부 판단
→ 태그 기반 관련 파일 조회
→ 선택한 생성 AI가 통합 변경 계획 생성
→ 변경 전후 미리보기 및 사용자 승인
→ 업로드된 프로젝트 사본에 적용
→ ZIP으로 결과 다운로드
```

생성 AI 분해 또는 Jev 호출에 실패하면 로컬 규칙 기반 분석으로 전환합니다. Jev 키가 없어도 서비스의 기본 에이전트 흐름을 사용할 수 있습니다.

## 주요 기능

- 프로젝트별 디렉터리 업로드, 전체·부분 재동기화 및 ZIP 내보내기
- 실제 디렉터리 구조 기반 파일 탐색기와 코드·텍스트 편집
- 파일 경로, 언어, 코드 구조, import와 어노테이션 기반 다중 태그 분석
- 프로젝트 대화, 인수인계, 메모와 명시적 프로젝트 기억 관리
- OpenAI, Google Cloud Vertex AI, 로컬 Ollama 생성 모델 선택
- 생성 AI 작업 분해와 Jev 기반 작업 검증·태그·위험도 판단
- 파일 생성·수정·이동·삭제 권한과 실행 모드 설정
- 적용 전 변경 파일 및 전후 차이 확인
- 실행 제공자, 모델, 토큰, 판단, 작업 분해 및 결과 기록
- 로컬 전용 또는 외부 AI 전송 허용 개인정보 모드
- Swagger/OpenAPI와 공통 API 응답

## 기술 구성

| 구분 | 기술 |
|---|---|
| Frontend | React, TypeScript, Vite |
| Backend | Java 17, Spring Boot 3.5, Spring Data JPA |
| Database | H2 File Database |
| AI | OpenAI Responses API, Vertex AI Gemini, Ollama |
| Decision | TypeSafe Jev via OpenRouter, local heuristic fallback |
| API Docs | Springdoc OpenAPI, Swagger UI |

## 프로젝트 구조

```text
LocalFlow/
├─ localflow-frontend/       # React 웹 클라이언트
├─ localflow-backend/        # Spring Boot API·에이전트 서버
├─ ollama-fastapi-backend/   # 초기 Ollama 연동 실험 코드(레거시·실행 대상 아님)
├─ .github/                  # 이슈·PR 템플릿
└─ .portfolio/               # AI 작업 컨텍스트에서 제외된 포트폴리오 기록
```

## 브랜치 운영

```text
feature/* 또는 fix/*
→ dev PR 및 기능 통합
→ dev에서 빌드·테스트
→ dev → main PR
→ main 배포
```

- `dev`: 일상적인 개발과 기능 통합의 기준 브랜치
- `main`: 검증을 마친 배포 가능 코드만 유지하는 브랜치
- `main`에는 직접 푸시하지 않고 반드시 `dev → main` PR을 사용합니다.
- Railway 운영 환경은 `main`, 필요하면 별도의 스테이징 환경은 `dev`를 바라보도록 설정합니다.

## 배포 구성

이 저장소는 서로 분리된 프론트엔드·백엔드·로컬 실험 코드를 한곳에서 관리하는 모노레포입니다. 수업 프로젝트와 MVP에서는 변경 이력과 이슈를 한곳에서 관리할 수 있어 현재 구성을 유지합니다.

Railway에서는 하나의 프로젝트 안에 다음 두 서비스를 만들고 동일한 저장소의 서로 다른 Root Directory를 지정합니다.

| Railway 서비스 | Root Directory | 용도 |
|---|---|---|
| Frontend | `/localflow-frontend` | React 정적 웹 서비스 |
| Backend | `/localflow-backend` | Spring Boot API 및 에이전트 실행 |

`ollama-fastapi-backend`는 현재 Spring 백엔드의 Ollama 기능으로 대체된 초기 실험 코드입니다. 참고용 레거시로만 남겨 두며 개발·배포 실행 대상에서 제외합니다. Railway에 배포한 서버는 사용자 PC의 `localhost` Ollama에 직접 접근할 수 없으므로, 외부 체험 환경에서는 OpenAI 또는 Vertex AI를 사용합니다.

업로드 파일과 H2 데이터베이스를 재배포 후에도 유지하려면 Backend에 Railway Volume을 연결하고 `LOCALFLOW_DB_URL`, `LOCALFLOW_WORKSPACE_ROOT`를 해당 마운트 경로 아래로 지정해야 합니다. 실제 배포 전에는 프론트엔드 정적 서버, Railway의 `PORT`, 공개 프론트 도메인 CORS 및 `VITE_API_BASE_URL`도 함께 설정합니다.

## 빠른 시작

### 요구 환경

- JDK 17
- Node.js 20 이상 및 npm
- 선택 사항: 로컬 Ollama
- 선택 사항: OpenAI, Vertex AI, OpenRouter API 자격 증명

### 1. 저장소 내려받기

```bash
git clone https://github.com/junjinyun/LocalFlow.git
cd LocalFlow
```

### 2. 백엔드 설정 및 실행

```powershell
cd localflow-backend
Copy-Item .env.example .env
```

`.env`에 사용할 제공자의 값만 입력합니다. `.env.example`은 설명용 템플릿이며 애플리케이션이 로드하지 않습니다.

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat bootRun
```

Linux·macOS에서는 다음 명령을 사용할 수 있습니다.

```bash
cd localflow-backend
cp .env.example .env
./gradlew bootRun
```

### 3. 프론트엔드 실행

```bash
cd localflow-frontend
npm install
npm run dev
```

- 웹 화면: `http://localhost:5173`
- 백엔드 상태: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- H2 Console: `http://localhost:8080/h2-console`

## AI 환경 변수

전체 항목과 설명은 [`localflow-backend/.env.example`](localflow-backend/.env.example)에 있습니다.

| 제공자 | 주요 환경 변수 | 비고 |
|---|---|---|
| OpenAI | `OPENAI_API_KEY`, `OPENAI_MODEL` | 모델 목록은 `OPENAI_MODELS`로 변경 |
| Vertex AI | `VERTEX_AI_SERVICE_ACCOUNT_BASE64`, `VERTEX_AI_PROJECT` | 서비스 계정 JSON 전체를 Base64로 입력 |
| Ollama | `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_PROBE_MODEL`, `OLLAMA_NUM_CTX` | 3B 호출 점검과 4B 실제 작업을 분리, API 키 불필요 |
| Jev/OpenRouter | `OPENROUTER_API_KEY`, `JEV_MODEL` | 미설정 시 로컬 판단 사용 |

API 키, 실제 `.env`, 서비스 계정 JSON과 Base64 값은 커밋하지 않습니다.

## 개인정보 및 권한

- 업로드된 프로젝트 사본만 수정하며 사용자 PC의 원본 파일에 직접 접근하지 않습니다.
- 기본 개인정보 모드는 `LOCAL_ONLY`이며 Ollama만 실행할 수 있습니다.
- `LOCAL_ONLY`에서는 `localhost`, `127.0.0.0/8`, `::1` 주소만 허용하며 Ollama Cloud 모델은 차단합니다.
- OpenAI, Vertex AI 또는 Jev를 사용하려면 프로젝트 설정에서 외부 AI 전송을 허용해야 합니다.
- 읽기, 생성, 수정, 이동, 삭제 권한은 `허용`, `확인`, `거부`로 설정할 수 있습니다.
- 승인 대기 화면에서 변경 전후 내용을 확인한 뒤 적용할 수 있습니다.

## 지원 범위

MVP는 소스 코드, 텍스트, 설정 파일과 Word/HWP 파일 저장을 지원합니다. Word/HWP 본문 분석, PDF·이미지 분석, 생성 코드 명령 실행과 자동 테스트는 확장 범위입니다.

## 검증

```powershell
cd localflow-backend
.\gradlew.bat test --no-daemon
```

```bash
cd localflow-frontend
npm run build
```

세부 백엔드·프론트엔드 설명은 각각의 README를 참고하세요.

- [Backend README](localflow-backend/README.md)
- [Frontend README](localflow-frontend/README.md)
