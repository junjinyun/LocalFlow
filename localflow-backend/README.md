# LocalFlow Backend

LocalFlow AI의 Spring Boot 백엔드입니다.

업로드된 프로젝트 디렉터리를 로컬 작업공간으로 관리하고, 파일 인덱스·채팅·프로젝트 기억·권한 설정과 프롬프트 단위 AI 에이전트 실행을 제공합니다.

## 구현 범위

- 프로젝트 생성·조회·수정·삭제 및 프로젝트별 실행/파일 권한 설정
- 디렉터리 초기 업로드와 재동기화
  - SHA-256 기준 추가·수정·유지·삭제 감지
  - 선택 범위 동기화 지원
  - `node_modules`, `.git`, 빌드 산출물, 이미지·바이너리 제외
  - 환경 파일, 개인 키, 서비스 계정 파일 제외
- 코드·텍스트·설정·Word/HWP 파일 저장과 다운로드
  - Word/HWP는 현재 저장·다운로드만 지원하며 본문 추출은 확장 범위
  - PDF와 이미지 분석은 MVP 제외
- Java/Python/JavaScript/TypeScript 계열 기본 코드 심볼 인덱싱
- 텍스트 파일 생성·수정·이동·삭제 및 작업 완료 ZIP 다운로드
- 프로젝트별 프롬프트/인수인계 기록과 명시적 기억 CRUD
- OpenRouter의 TypeSafe Jev 또는 로컬 휴리스틱을 이용한 작업 유형·대상 파일·위험도 판단
- OpenAI Responses API, Google Vertex AI Gemini, 로컬 Ollama 중 선택 실행
- AI JSON 계획 검증, 사용자 승인 대기, 업로드된 작업 사본에 파일 생성·수정·이동·삭제 적용
- 제공자·모델·입출력 토큰·결과·오류가 포함된 실행 기록
- Swagger/OpenAPI, 공통 응답·예외 처리, 로컬 H2 파일 DB

## 실행

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat bootRun
```

### AI 환경 변수

`localflow-backend/.env.example`을 `.env`로 복사한 뒤 실행할 제공자에 필요한 값을 입력합니다. Spring Boot는 정확히 `.env`만 자동으로 읽으며 `.env.example`은 로드하지 않습니다. `.env`는 Git에서 제외되고 `.env.example`만 커밋됩니다. 키나 서비스 계정 JSON은 프로젝트에 업로드하거나 Git에 커밋하지 않습니다.

```powershell
Copy-Item .env.example .env
```

운영체제나 IDE에서 같은 이름의 환경 변수를 주입하면 해당 값을 사용할 수도 있습니다. 아래는 PowerShell에서 직접 주입하는 예시입니다.

```powershell
# OpenAI
$env:OPENAI_API_KEY='sk-...'
$env:OPENAI_MODEL='gpt-5-mini'

# OpenRouter TypeSafe Jev 의사결정 계층 (선택 사항, 없으면 로컬 규칙 사용)
$env:OPENROUTER_API_KEY='sk-or-v1-...'
$env:JEV_BASE_URL='https://openrouter.ai/api/alpha/decisions'
$env:JEV_MODEL='typesafe/jev-1.13'

# Google Cloud Vertex AI 서비스 계정 JSON 전체를 Base64 한 줄로 변환
$serviceAccountJson='C:\secure\vertex-service-account.json'
$env:VERTEX_AI_SERVICE_ACCOUNT_BASE64=[Convert]::ToBase64String(
    [IO.File]::ReadAllBytes($serviceAccountJson)
)
$env:VERTEX_AI_PROJECT='my-gcp-project-id'
$env:VERTEX_AI_LOCATION='us-central1'
$env:VERTEX_AI_MODEL='gemini-2.5-flash'

# 로컬 Ollama (API 키 불필요)
$env:OLLAMA_ENABLED='true'
$env:OLLAMA_BASE_URL='http://localhost:11434'
$env:OLLAMA_MODEL='qwen3.5:4b-q4_K_M'
$env:OLLAMA_PROBE_MODEL='qwen2.5-coder:3b'
$env:OLLAMA_NUM_CTX='8192'
$env:OLLAMA_NUM_PREDICT='4096'
$env:OLLAMA_REQUEST_TIMEOUT='10m'
$env:OLLAMA_QUEUE_TIMEOUT='15m'
$env:OLLAMA_MAX_CONCURRENT_REQUESTS='1'

.\gradlew.bat bootRun
```

`OPENAI_BASE_URL`, `JEV_BASE_URL`도 호환 서버를 사용할 때 재정의할 수 있습니다. Jev 판단은 OpenRouter Decisions API를 사용하며 모델 ID는 `typesafe/jev-1.13`입니다. 기본 개인정보 모드는 `LOCAL_ONLY`이므로 OpenAI·Vertex AI·Jev를 사용하려면 프로젝트 설정에서 `EXTERNAL_ALLOWED`로 바꿔야 합니다. 로컬 전용에서는 `localhost`, `127.0.0.0/8`, `::1` Ollama 주소만 허용하고 `:cloud`, `*-cloud` 계열 모델을 차단합니다.

Ollama는 역할별로 두 모델을 사용합니다. `OLLAMA_PROBE_MODEL`은 AI 제공자 상태를 수동 새로고침할 때 짧은 실제 요청으로 호출 경로와 오류 여부를 점검하고, `OLLAMA_MODEL`은 프롬프트 분해와 파일 작업 결과를 생성합니다. 기본값은 각각 `qwen2.5-coder:3b`, `qwen3.5:4b-q4_K_M`입니다.

Ollama 작업 요청은 기본적으로 한 번에 하나만 실행됩니다. 동시에 요청하면 먼저 시작한 요청이 끝날 때까지 대기열에서 기다리며, 최초 모델 로딩과 대기 상태는 실행 타임라인에 표시됩니다. 메모리가 부족하면 `OLLAMA_NUM_CTX=8192`와 `AI_CONTEXT_MAX_CHARS=16000` 정도로 낮추고, 충분한 장비에서만 동시 실행 수를 높이세요. `OLLAMA_REQUEST_TIMEOUT`은 실제 모델 응답 제한, `OLLAMA_QUEUE_TIMEOUT`은 앞선 작업을 기다리는 제한입니다.

#### Vertex AI 서비스 계정 준비

현재 구현은 Agent Builder나 Agent Engine이 아니라 Vertex AI의 생성형 AI 모델 `generateContent` API를 직접 호출합니다. 따라서 별도의 AI Agent Platform 활성화는 필요하지 않으며 다음 순서로 준비합니다.

1. 결제가 연결된 Google Cloud 프로젝트를 선택하거나 생성합니다.
2. 해당 프로젝트에서 **Vertex AI API**(`aiplatform.googleapis.com`)를 활성화합니다.
3. 전용 서비스 계정을 생성하고 프로젝트에 **Vertex AI User**(`roles/aiplatform.user`) 역할을 부여합니다.
4. 서비스 계정의 JSON 키를 생성해 내려받습니다.
5. JSON 파일 **전체**를 Base64 한 줄 문자열로 변환해 `.env`의 `VERTEX_AI_SERVICE_ACCOUNT_BASE64`에 입력합니다. `private_key` 항목만 변환하는 것이 아닙니다.
6. 같은 프로젝트 ID를 `VERTEX_AI_PROJECT`에 입력하고 백엔드를 다시 시작합니다.

Base64는 암호화가 아니므로 변환 전 JSON과 변환 결과를 모두 비밀 키처럼 취급해야 합니다. 애플리케이션은 Base64 값을 메모리에서 해석하며 임시 JSON 파일을 만들지 않습니다.

### 에이전트 실행 API

1. `POST /api/projects/{projectId}/agent-runs`로 초안을 만듭니다.
2. `POST /api/projects/{projectId}/agent-runs/{runId}/execute`와 `{"approved":false}`로 실행을 등록합니다. API는 `202 Accepted`와 `PENDING` 상태를 즉시 반환하고 실제 AI 작업은 백그라운드에서 계속됩니다.
3. `GET /api/projects/{projectId}/agent-runs/active`로 탭 이동이나 새로고침 이후에도 실행 중·승인 대기 작업을 복구할 수 있습니다.
4. 상태가 `WAITING_APPROVAL`이면 계획을 검토한 뒤 같은 실행 API에 `{"approved":true}`를 보내 업로드 사본에 적용합니다.

파일 읽기 정책이 `CONFIRM`이면 계획 생성 전에도 한 번 승인 대기할 수 있습니다. `DENY` 작업은 승인 여부와 관계없이 실행되지 않습니다. MVP에서는 한 프로젝트에 실행 중 또는 승인 대기 작업을 하나만 허용하며, 중복 생성 요청은 `409 Conflict`로 거부합니다.

- 상태 확인: `http://localhost:8080/api/health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- H2 Console: `http://localhost:8080/h2-console`

H2 JDBC URL은 기본적으로 `jdbc:h2:file:./data/localflow`이며, 업로드 파일은 `./workspace/{projectId}/source`에 저장됩니다. 두 디렉터리는 Git에서 제외됩니다.

## 디렉터리 동기화 요청

`PUT /api/projects/{projectId}/files/sync`에 `multipart/form-data`로 다음 값을 보냅니다.

- `files`: 업로드 파일 목록
- `manifest`: `{"relativePaths":[...]}` 구조의 JSON 파트. 각 파일과 같은 순서의 상대 경로 목록
- `fullSync`: 기본 `true`. 누락된 기존 파일을 삭제로 처리할지 여부
- `scope`: 선택값. 특정 하위 경로만 비교할 때 사용

파일 생성·수정·이동·삭제 API의 `approved` 값은 권한 정책이 `CONFIRM`일 때 사용자의 확인 팝업 승인을 표현합니다.

프론트엔드는 전송 전에 `node_modules`, `.git`, 빌드 산출물, 바이너리와 민감정보 파일을 제외합니다. 백엔드에서도 같은 규칙을 다시 검증하며 프로젝트당 최대 5,000개, 파일당 30MB, 전체 250MB 제한을 적용합니다.

## 패키지 구조

```text
com.localflow
├─ global
│  ├─ common
│  ├─ config
│  ├─ controller
│  └─ error
└─ domain
   ├─ agent
   │  ├─ controller
   │  ├─ domain
   │  ├─ dto
   │  └─ service
   ├─ chat
   ├─ memory
   ├─ project
   │  ├─ controller
   │  ├─ dto
   │  ├─ entity
   │  ├─ repository
   │  └─ service
   ├─ provider
      ├─ controller
      ├─ domain
      ├─ dto
      ├─ port
      └─ service
   └─ workspace
      ├─ config
      ├─ controller
      ├─ domain
      ├─ dto
      ├─ entity
      ├─ repository
      └─ service
```

## 테스트

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat clean test --no-daemon
```
