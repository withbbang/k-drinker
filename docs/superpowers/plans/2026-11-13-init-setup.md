# 프로젝트 초기 세팅 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `apps/api`(Spring Boot)와 `apps/web`(Next.js) 뼈대를 만들고, 공통 응답 규약과 `GET /api/v1/health` 연결을 테스트로 확인한다.

**Architecture:** api는 `com.kdrinker.common`에 응답 래퍼·에러 코드·예외 처리기를 두고 `com.kdrinker.health`에 첫 엔드포인트를 둔다. web은 `/api/*`를 `API_INTERNAL_URL`로 rewrite하고, 클라이언트 React Query가 `lib/api.ts`의 `apiFetch`로 호출한다. 구성은 `../lottery-picker/apps/*`를 기준으로 하되 로또 전용 코드는 가져오지 않는다.

**Tech Stack:** Next.js 16.4.5, React 19.3.0, TypeScript 5, Tailwind 4, pnpm 12.15.0, Vitest 5, Testing Library / Spring Boot 4.1.2, Java 17, Gradle 9.8.2(wrapper), JPA, Flyway, H2(local) / MySQL(prod), Lombok, JUnit 5 + AssertJ

**Spec:** `docs/superpowers/specs/2026-11-13-init-setup-design.md`

## Global Constraints

- 버전은 위 Tech Stack 그대로 (lottery-picker와 동일).
- 규칙: `.claude/rules/api.md`, `.claude/rules/web.md`, `.claude/rules/testing.md`, 응답 포맷은 `.claude/skills/api-conventions/SKILL.md`.
- `.env*` 파일은 만들지 않는다. `apps/web/.env.example`, `apps/api/.env.example`만.
- 커밋 메시지는 `.gitmessage.txt` 형식 `Type(scope) : 제목` (50자 이내), 본문 줄 72자 이내, 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 로또·텔레그램·인증·Docker 관련 코드와 설정은 가져오지 않는다.

## Review Focus

1. API 서버가 꺼져 있거나 프록시가 HTML 502를 돌려줄 때 → `apiFetch`가 JSON 파싱 에러 대신 `ApiError("COMMON_INTERNAL_ERROR", ...)`를 던지고 화면은 오류 문구를 보여준다. (Task 2 테스트)
2. 없는 경로 `GET /api/v1/nope` → 404 + `{ success: false, error: { code: "COMMON_NOT_FOUND" } }` (Spring 기본 에러 페이지가 아님). (Task 1 테스트)
3. 처리되지 않은 예외 → 500 + `COMMON_INTERNAL_ERROR`, 메시지에 예외 클래스명·스택트레이스 없음. (Task 1 테스트)
4. 204 응답 → `apiFetch`가 본문 파싱 없이 `undefined` 반환. (Task 2 테스트)
5. `API_INTERNAL_URL` 없이 `pnpm build`/`pnpm dev` → 어떤 변수가 필요한지 알려주는 에러로 즉시 실패. (Task 2 수동 확인)

---

### Task 1: apps/api 스캐폴딩 + 공통 응답 + health

**Files:**
- Create: `apps/api/{build.gradle,settings.gradle,gradlew,gradlew.bat,gradle/wrapper/*,.gitignore,.gitattributes,.env.example}`
- Create: `apps/api/src/main/java/com/kdrinker/ApiApplication.java`
- Create: `apps/api/src/main/java/com/kdrinker/common/{ApiResponse,ErrorCode,BusinessException,GlobalExceptionHandler}.java`
- Create: `apps/api/src/main/java/com/kdrinker/health/{HealthController,HealthResponse}.java`
- Create: `apps/api/src/main/resources/{application.yml,application-local.yml,application-prod.yml}`, `db/migration/.gitkeep`
- Test: `apps/api/src/test/java/com/kdrinker/{ApiApplicationTests,health/HealthControllerTest,common/GlobalExceptionHandlerTest}.java`

**Interfaces:**
- Produces (HTTP): `GET /api/v1/health` → 200 `{"success":true,"data":{"status":"UP"}}`
- Produces (Java): `ApiResponse<T>(boolean success, T data, ErrorBody error)` + `success(T)`, `failure(ErrorCode)`, `failure(ErrorCode, List<FieldReason>)`; `ErrorCode { COMMON_VALIDATION_FAILED(400), COMMON_NOT_FOUND(404), COMMON_INTERNAL_ERROR(500), AUTH_REQUIRED(401) }` (`getStatus()`, `getMessage()`); `BusinessException(ErrorCode)`

- [ ] **Step 1: 빌드 설정**
  - gradle wrapper는 `../lottery-picker/apps/api`의 `gradlew`, `gradlew.bat`, `gradle/wrapper/`, `.gitignore`, `.gitattributes`를 복사.
  - `build.gradle`: lottery-picker 것에서 `group = 'com.kdrinker'`, `restclient`·`h2console` 의존성과 `totpSetup` 태스크, `app.scheduling.enabled` 시스템 프로퍼티 제거. `settings.gradle`: `rootProject.name = 'api'`.
  - `application.yml`: `spring.application.name: k-drinker-api`, `profiles.default: local`, `jpa.open-in-view: false`. `application-local.yml`: `jdbc:h2:file:./.data/kdrinker;MODE=MySQL`, `ddl-auto: validate`. `application-prod.yml`: `${DB_URL}`·`${DB_USERNAME}`·`${DB_PASSWORD}`.
  - `.env.example`: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL=jdbc:mysql://localhost:3306/kdrinker`, `DB_USERNAME=`, `DB_PASSWORD=`.
  - `.gitignore`에 `.data/` 포함 확인.

- [ ] **Step 2: 실패하는 테스트 작성**
  - `ApiApplicationTests.contextLoads()` (`@SpringBootTest`).
  - `HealthControllerTest` (`@WebMvcTest(HealthController.class)` + `@Import(GlobalExceptionHandler.class)`): `헬스체크는_UP을_돌려준다()` → status 200, `$.success == true`, `$.data.status == "UP"`, `$.error` 없음.
  - `GlobalExceptionHandlerTest` (`@WebMvcTest` + 테스트 전용 `@RestController`를 `@Import`):
    - `없는_경로는_COMMON_NOT_FOUND_404다()` → `GET /api/v1/nope`: 404, `$.success == false`, `$.error.code == "COMMON_NOT_FOUND"`
    - `처리되지_않은_예외는_500이고_내부정보를_숨긴다()` → `IllegalStateException("secret-detail")` 던지는 엔드포인트: 500, `$.error.code == "COMMON_INTERNAL_ERROR"`, 응답 본문에 `secret-detail`·`IllegalStateException` 없음
    - `BusinessException은_ErrorCode의_상태와_메시지로_변환된다()` → `BusinessException(AUTH_REQUIRED)`: 401, `$.error.message == "로그인이 필요합니다."`
    - `검증_실패는_fields를_포함한다()` → `@Valid` 요청 record(`@NotBlank String name`)에 `{}`: 400, `$.error.code == "COMMON_VALIDATION_FAILED"`, `$.error.fields[0].field == "name"`

- [ ] **Step 3: 실패 확인** — Run: `cd apps/api && ./gradlew test` / Expected: 컴파일 실패(클래스 없음)

- [ ] **Step 4: 구현**
  - `common/*`: lottery-picker의 같은 파일을 패키지만 바꿔 옮기고 `ErrorCode`는 Interfaces의 4개만 (메시지: `"요청 값이 올바르지 않습니다."`, `"요청한 리소스를 찾을 수 없습니다."`, `"서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."`, `"로그인이 필요합니다."`).
  - `HealthResponse(String status)` record, `HealthController`: `@GetMapping("/api/v1/health") ApiResponse<HealthResponse> health()` → `ApiResponse.success(new HealthResponse("UP"))`.

- [ ] **Step 5: 통과 확인** — Run: `cd apps/api && ./gradlew test && ./gradlew build` / Expected: `BUILD SUCCESSFUL`, 테스트 6개 통과

- [ ] **Step 6: 커밋** — `git add apps/api && git commit` 제목 `Chore(api) : Spring Boot 스캐폴딩과 공통 응답 추가`

---

### Task 2: apps/web 스캐폴딩 + apiFetch + health 화면

**Files:**
- Create: `apps/web/{package.json,pnpm-workspace.yaml,tsconfig.json,next.config.ts,eslint.config.mjs,.prettierrc.json,.prettierignore,.gitignore,.env.example,vitest.config.mts,vitest.setup.ts,next-env.d.ts}`
- Create: `apps/web/app/{layout.tsx,page.tsx,globals.css}`
- Create: `apps/web/types/api/{common,health}.ts`, `apps/web/lib/api.ts`, `apps/web/components/providers/QueryProvider.tsx`, `apps/web/hooks/use-health.ts`, `apps/web/components/health/HealthStatus.tsx`
- Test: `apps/web/lib/api.test.ts`, `apps/web/components/health/HealthStatus.test.tsx`

**Interfaces:**
- Consumes: Task 1의 `GET /api/v1/health` 응답
- Produces: `apiFetch<T>(path: string, init?: RequestInit): Promise<T>`, `class ApiError extends Error { code: string }`, `ApiResponse<T>` 타입, `HealthResponse = { status: string }`, `useHealth()` (React Query 키 `['health']`), `<QueryProvider>`

- [ ] **Step 1: 프로젝트 설정**
  - `package.json`: lottery-picker 것에서 `name: "web"`, `leaflet`·`@types/leaflet` 제거, 나머지 의존성·스크립트·`packageManager` 동일.
  - `next.config.ts`: lottery-picker 것에서 `output: "standalone"`과 그 주석 제거, `API_INTERNAL_URL` 없으면 throw하는 rewrites 유지.
  - `pnpm-workspace.yaml`, `tsconfig.json`, `eslint.config.mjs`, `.prettierrc.json`, `.prettierignore`, `.gitignore`, `vitest.config.mts`, `vitest.setup.ts`는 lottery-picker에서 그대로 복사. `.env.example`: `API_INTERNAL_URL=http://localhost:8080`.
  - Run: `cd apps/web && pnpm install` / Expected: `pnpm-lock.yaml` 생성

- [ ] **Step 2: 실패하는 테스트 작성**
  - `lib/api.test.ts` (`vi.stubGlobal("fetch", ...)`):
    - `it("returns data when success is true")` → `{success:true,data:{status:"UP"}}` → `{status:"UP"}`
    - `it("throws ApiError with code and message when success is false")` → `code == "COMMON_NOT_FOUND"`, `message == "요청한 리소스를 찾을 수 없습니다."`
    - `it("returns undefined for 204")` → `json()`이 호출되지 않음
    - `it("throws COMMON_INTERNAL_ERROR when the body is not JSON")` → status 502, 본문 `<html>Bad Gateway</html>` → `ApiError` code `"COMMON_INTERNAL_ERROR"`, message `"서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."`
  - `components/health/HealthStatus.test.tsx` (새 `QueryClient({ defaultOptions: { queries: { retry: false } } })`로 감싸고 `fetch` 모킹):
    - `it("shows the API status when the server is up")` → `findByText("API 상태: UP")`
    - `it("shows an error message when the request fails")` → 502 HTML → `findByRole("alert")`에 `"서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."`

- [ ] **Step 3: 실패 확인** — Run: `cd apps/web && pnpm test` / Expected: FAIL (모듈 없음)

- [ ] **Step 4: 구현**
  - `types/api/common.ts`: lottery-picker 그대로. `types/api/health.ts`: `HealthResponse`.
  - `lib/api.ts`: lottery-picker 것 + `response.json()` 실패 시 위 문구로 `ApiError("COMMON_INTERNAL_ERROR", ...)`.
  - `QueryProvider.tsx`: `"use client"`, `useState(() => new QueryClient(...))`, `retry`는 `COMMON_NOT_FOUND`·`AUTH_REQUIRED`면 재시도 안 함, 그 외 3회. 로그인 리다이렉트는 넣지 않는다 (인증은 비목표).
  - `hooks/use-health.ts`: `useQuery({ queryKey: ['health'], queryFn: () => apiFetch<HealthResponse>("/api/v1/health") })`.
  - `HealthStatus.tsx`: `"use client"`; 로딩 `"확인 중…"`, 성공 `"API 상태: {status}"`, 실패 `<p role="alert">{error.message}</p>`. Tailwind만.
  - `app/layout.tsx`: `lang="ko"`, `metadata.title = "k-drinker"`, `<QueryProvider>`로 감쌈. `app/page.tsx`: 서버 컴포넌트, 제목 `k-drinker` + `<HealthStatus />`. `globals.css`: `@import "tailwindcss";`.

- [ ] **Step 5: 통과 확인** — Run: `cd apps/web && pnpm lint && pnpm test && API_INTERNAL_URL=http://localhost:8080 pnpm build` / Expected: 모두 성공, 테스트 6개 통과

- [ ] **Step 6: 수동 확인** — `pnpm build`를 `API_INTERNAL_URL` 없이 실행 → `API_INTERNAL_URL 이 설정되지 않았습니다` 에러로 실패. api `./gradlew bootRun` + web `API_INTERNAL_URL=http://localhost:8080 pnpm dev` → `http://localhost:3000`에 `API 상태: UP`.

- [ ] **Step 7: 문서·커밋**
  - `CLAUDE.md` 명령 섹션의 web `dev`·`build`에 `API_INTERNAL_URL=http://localhost:8080` 필요 표기 (lottery-picker 형식).
  - `git add apps/web CLAUDE.md && git commit` 제목 `Chore(web) : Next.js 스캐폴딩과 API 연결 추가`
