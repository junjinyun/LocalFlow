# LocalFlow Frontend

업로드한 프로젝트 사본을 프로젝트 단위로 관리하는 React·TypeScript 프론트엔드입니다. Spring 백엔드의 프로젝트, 파일, 채팅, 기억, 설정, 에이전트 실행 초안 API와 연결되어 있습니다.

## 실행

먼저 `localflow-backend`에서 Spring 서버를 실행합니다.

```powershell
cd ..\localflow-backend
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
.\gradlew.bat bootRun
```

다른 터미널에서 프론트엔드를 실행합니다.

```powershell
cd localflow-frontend
npm install
npm run dev
```

브라우저에서 `http://localhost:5173`으로 접속합니다. 개발 서버는 `/api` 요청을 `http://localhost:8080`으로 전달합니다.

백엔드 주소를 따로 지정하려면 `.env.local`에 다음 값을 작성합니다.

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 연결된 기능

- 프로젝트 생성·수정·삭제
- 프로젝트 단건 재조회와 서버 연결 상태 표시
- 프로젝트 디렉터리 업로드와 전체·부분 동기화
- 업로드 전 의존성·빌드·바이너리·민감 파일 필터링과 제외 내역 표시
- 실제 전송률 및 서버 분석·인덱싱 단계 표시
- 파일 검색·내용 조회·생성·수정·이동·삭제·다운로드
- 작업 완료 ZIP 다운로드
- 프로젝트 채팅, 메모, 인수인계 기록
- 프로젝트 기억 생성·수정·활성화·삭제
- 활성 기억만 조회하는 서버 필터
- 실행 모드, 개인정보 모드, 파일 작업별 권한 설정
- AI 제공자 환경 설정 상태 표시
- OpenAI·Vertex AI·Gemini CLI·Ollama 실행 요청
- Jev/로컬 판단 결과에 따른 실행 계획 생성, 승인 및 파일 적용
- 실행 결과·오류·대상 파일 작업·토큰 사용량 조회와 취소
- Jev 판단 유형·대상 파일·위험도 표시 및 실행 단건 상태 재조회
- 파일 동기화 변경 상세와 코드 심볼 줄 이동

## 빌드

```powershell
npm run build
```

Word/HWP 본문 미리보기, PDF·이미지 분석, 생성 코드의 명령 실행·자동 테스트는 아직 연결하지 않았습니다.
