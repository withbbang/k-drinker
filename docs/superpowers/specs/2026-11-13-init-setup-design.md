# 프로젝트 초기 세팅 설계

- 날짜: 2026-11-13
- 브랜치: `feature/init-setup` (`dev`에서 분기)
- 상태: 승인됨

## 의도

전통주 후기 SNS 자동화 업로드 서비스(k-drinker)의 첫 기능을 바로 얹을 수 있도록 `apps/web`·`apps/api` 뼈대를 만든다. `.claude/rules`와 `api-conventions` 스킬이 전제하는 공통 코드(응답 래퍼, `ErrorCode`, `apiFetch`)를 이번에 함께 둬서 첫 기능 때 기반 작업이 섞이지 않게 한다.

## 목표

- `apps/web`: Next.js 16.4.5 / React 19.3 / TypeScript / Tailwind 4 / pnpm. ESLint + Prettier, Vitest + Testing Library. `pnpm lint`·`pnpm test`·`pnpm build`가 통과한다.
- `apps/api`: Spring Boot 4.1.2 / Java 17 / Gradle, 패키지 `com.kdrinker`. web·JPA·validation·Flyway·Lombok, DB는 local H2 / prod MySQL. `./gradlew test`·`./gradlew build`가 통과한다.
- 공통 코드
  - api: `ApiResponse` 래퍼, `ErrorCode` enum(`COMMON_*`), `BusinessException`, `GlobalExceptionHandler`
  - web: `lib/api.ts`의 `apiFetch<T>()`·`ApiError`, React Query `QueryProvider`, `/api/*` → `API_INTERNAL_URL` rewrites
- 연결 확인: `GET /api/v1/health` → `{ success: true, data: { status: "UP" } }`, 웹 첫 화면이 이를 호출해 상태를 표시. 양쪽에 테스트.

## 비목표

- Docker / 배포 설정, CI
- 인증, SNS 연동, 전통주·후기 도메인 기능

## 제약

- 버전·구성은 lottery-picker(`../lottery-picker`)와 맞춘다. 규칙은 `.claude/rules/*.md`, 응답 포맷은 `api-conventions` 스킬.
- `.env*`는 만들지 않고 `.env.example`만 둔다.

## 접근 방식

| 방식 | 장단점 | 선택 |
|---|---|---|
| 최소 템플릿만 (create-next-app + Spring Initializr 기본) | 빠르지만 rules가 요구하는 공통 코드가 없어 첫 기능에서 다시 만들어야 함 | ✗ |
| **공통 코드 + health 연결까지** | 첫 기능부터 규약대로 작성 가능, 양쪽 연결을 테스트로 확인 | ✓ |
| 배포(Docker·compose)까지 포함 | 서버 환경이 정해지지 않아 이름 | ✗ |

## 빌더 재량

- 정확한 패치 버전은 lottery-picker와 같거나 설치 시점 최신 호환 버전.
- 첫 화면 디자인은 최소한(상태 텍스트)으로.
