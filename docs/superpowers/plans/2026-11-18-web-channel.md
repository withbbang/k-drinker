# 담당 채널 분리와 웹 개요 화면 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 게시물마다 담당 채널(TELEGRAM/WEB)을 두어 웹에서 시작하거나 웹에서 저장한 게시물은 웹 개요 화면에서 확인·게시까지 끝내고, 실기기 검증에서 나온 화면 결함을 고친다.

**Architecture:** API는 `post.channel` 컬럼으로 담당을 저장하고, 텔레그램 알림·콜백은 `WEB` 게시물을 건너뛴다. 웹 확인·취소·재시도는 텔레그램 콜백과 같은 서비스(`PostActionService`)를 부르는 REST 엔드포인트로 연다. 웹은 `/posts/{id}` 개요 화면이 React Query 폴링으로 상태를 따라간다.

**Tech Stack:** Spring Boot 4.1 / JPA / Flyway / JUnit 5 · Next.js 16 App Router / React 19 / React Query 5 / Tailwind 4 / Vitest + Testing Library

**Spec:** `docs/superpowers/specs/2026-11-18-web-channel-design.md`

## Global Constraints

- 응답·커밋·주석은 한국어. 커밋 형식 `Type(scope) : 제목` (제목 50자 이내, 끝 마침표 없음), 본문 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 패키지 매니저는 pnpm. `.env*` 파일은 읽지도 고치지도 않는다.
- 도메인 로직은 `apps/api`에만. 웹은 호출·표시만.
- API 응답·에러는 `api-conventions` 스킬 형식. **새 에러 코드를 만들지 않는다**: 담당 불일치 409 `POST_NOT_EDITABLE`, 상태·버전 불일치 409 `POST_VERSION_CONFLICT`, 캡션 초과 400 `POST_CAPTION_TOO_LONG`.
- `PostStatus` 전이 표는 바꾸지 않는다. 웹 동작도 `version` 조건부 전이를 거친다(두 번 게시 금지).
- 담당 규칙: 생성 시 `source=TELEGRAM`→`TELEGRAM`, `source=SHARE`→`WEB`. 웹 수정 [저장] 성공 시 항상 `WEB`. `WEB`→`TELEGRAM`은 없다. 기존 행은 `TELEGRAM`.
- 텔레그램 [상세]·[수정]·[확인]·[취소] 동작은 `TELEGRAM` 게시물에서 지금과 같다.
- 웹 폴링: `RECEIVED`·`ANALYZING`·`PUBLISHING`일 때만 3000ms 간격.
- 문구(정확히):
  - 공유 11장 이상 버튼: `사진을 10장 이하로 줄여 주세요 (지금 N장)`
  - 텔레그램 담당 안내: `텔레그램에서 진행 중인 게시물입니다.`
  - 분석 중: `분석 중입니다`, 게시 중: `게시 중입니다`, 취소: `취소한 게시물입니다`
- API 테스트는 context-mode 훅 때문에 `ctx_execute`(shell)로 `./gradlew test ...`를 돌리고, 결과는 `build/test-results/test/*.xml`로 센다. 웹은 `pnpm test`, `pnpm lint`.

## Review Focus

1. 웹 담당으로 넘어간 뒤 늦게 도착한 분석·게시 완료 이벤트가 텔레그램 메시지를 보내는 경우 → 보내지 않아야 한다 (Task 1 `PostNotifierTest` 각 notify 메서드 WEB 케이스).
2. 텔레그램에 남은 옛 버튼으로 WEB 게시물을 [확인] → stale 처리, 게시되지 않음 (Task 1 `CallbackHandlerTest`).
3. 웹에서 [확인]을 두 번 빠르게 누르거나 이미 PUBLISHING인 게시물 confirm → 두 번째는 409 `POST_VERSION_CONFLICT` (Task 2 `PostActionServiceTest`).
4. 공유 화면에서 사진을 빼다가 0장이 되는 경우 → 마지막 1장의 [빼기]는 비활성 (Task 4).
5. 개요 화면을 연 채 다른 곳에서 상태가 바뀐 뒤 [확인] → 409 문구를 보이고 다시 불러옴 (Task 3).

---

### Task 1: 담당 채널 저장과 텔레그램 차단 (API)

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V10__post_channel.sql`
- Create: `apps/api/src/main/java/com/kdrinker/post/entity/PostChannel.java`
- Modify: `post/entity/Post.java`, `post/event/PostEditedEvent.java`, `post/service/PostEditService.java`, `post/service/PostQueryService.java`, `post/dto/PostDetailResponse.java`, `post/dto/PostSummaryResponse.java`, `telegram/PostNotifier.java`, `telegram/PostNotificationListener.java`, `telegram/CallbackHandler.java` (모두 `apps/api/src/main/java/com/kdrinker/` 아래)
- Test: `PostEditServiceTest`, `PostQueryServiceTest`, `PostNotifierTest`, `PostNotificationListenerTest`, `CallbackHandlerTest`, `PostRepositoryTest`(마이그레이션 확인), `PostEditIntegrationTest`

**Interfaces:**
- Produces: `enum PostChannel { TELEGRAM, WEB }`; `Post.getChannel()`, `Post.moveToWeb()`; `PostEditedEvent(Long postId, boolean handedOverToWeb)`; `PostNotifier.removeOverviewButtons(Long postId)` (`@Async`); `PostDetailResponse.channel`·`PostSummaryResponse.channel` (`String`, enum 이름, 레코드 마지막 컴포넌트로 추가).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `Post.receive(SHARE, …).getChannel() == WEB`, `Post.receive(TELEGRAM, …).getChannel() == TELEGRAM`.
  - `PostEditServiceTest`: TELEGRAM 게시물 저장 → 저장 후 `channel == WEB`, 발행된 `PostEditedEvent.handedOverToWeb() == true`. 이미 WEB인 게시물 저장 → `handedOverToWeb() == false`.
  - `PostNotifierTest`: WEB 게시물에 `notifyReceived`·`notifyReviewReady`·`notifyAnalysisFailed`·`notifyPublishing`·`notifyPublishFinished`·`notifyCanceled` → `sender.send` 호출 0회. `removeOverviewButtons(id)` → `sender.messageIds(id, OVERVIEW)`의 각 id에 `client.removeKeyboard` 호출, 하나가 예외여도 나머지 계속.
  - `PostNotificationListenerTest`: `onEdited(new PostEditedEvent(1L, true))` → `removeOverviewButtons(1L)`만, `notifyReviewReady` 없음. `(1L, false)` → 아무 호출 없음.
  - `CallbackHandlerTest`: WEB 게시물에 `ok`·`cancel`·`detail`·`edit`·`retry` 콜백 → stale 응답, `publishService.confirm` 호출 0회.
  - `PostQueryServiceTest`: 상세·목록 응답에 `channel` 값.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*PostEditServiceTest' --tests '*PostNotifierTest' --tests '*PostNotificationListenerTest' --tests '*CallbackHandlerTest' --tests '*PostQueryServiceTest'` → 컴파일 실패.
- [ ] **Step 3: 구현**
  - `V10__post_channel.sql`: `ALTER TABLE post ADD COLUMN channel VARCHAR(16) NOT NULL DEFAULT 'TELEGRAM';` (기존 행 TELEGRAM).
  - `Post.channel` (`@Enumerated(STRING)`, `nullable=false`, `length=16`), `receive`에서 source로 초기화, `moveToWeb()`.
  - `PostEditService.update`: `Snapshot`에 담당을 담고, 쓰기 트랜잭션의 `save` 안에서 `moveToWeb()`, 이벤트를 `new PostEditedEvent(id, snapshot.channel() == TELEGRAM)`로.
  - `PostNotifier`: `prepare`가 게시물 담당이 `WEB`이면 `Optional.empty()`(로그 debug). `notifyReviewReady`의 `captionLengthGuard.failIfTooLong`은 지금처럼 맨 앞에서 그대로 실행(담당과 무관한 도메인 검사). `removeOverviewButtons`는 지금 `notifyReviewReady` 안의 이전 버튼 제거 루프를 꺼낸 것(새 메시지 제외 조건 없이 전부).
  - `PostNotificationListener.onEdited`: `handedOverToWeb`이면 `removeOverviewButtons`, 아니면 아무것도 안 함. (수정 저장 뒤 게시물은 항상 WEB이라 새 개요를 텔레그램에 보내지 않는다.)
  - `CallbackHandler.dispatch`: 버전 확인 다음에 `post.getChannel() == WEB`이면 `stale()`.
  - `NotificationReconciler`는 고치지 않는다(`prepare`가 막는다). `NotificationReconcilerTest`에 WEB 게시물이 메시지를 만들지 않는 케이스 하나 추가.
- [ ] **Step 4: 실행해 통과 확인** — 위 명령 + `--tests '*PostEditIntegrationTest' --tests '*PostRepositoryTest' --tests '*NotificationReconcilerTest'` 통과, 이어서 전체 `./gradlew test` 실패 0.
- [ ] **Step 5: 커밋** — `Feat(api) : 게시물 담당 채널과 웹 담당 텔레그램 차단`

### Task 2: 웹 확인·취소·재시도 API

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/post/service/PostActionService.java`, `apps/api/src/main/java/com/kdrinker/post/dto/PostActionRequest.java`
- Modify: `post/controller/PostController.java`, `telegram/CallbackHandler.java`
- Test: Create `apps/api/src/test/java/com/kdrinker/post/service/PostActionServiceTest.java`; Modify `PostControllerTest`, `CallbackHandlerTest`, `CallbackCancelIntegrationTest`

**Interfaces:**
- Consumes: Task 1 `PostChannel`, `Post.getChannel()`.
- Produces:
  - `record PostActionRequest(@NotNull Integer version)`
  - `PostActionService` (`@Service`):
    - `ConfirmResult cancel(Long postId, int version)` — `PENDING_REVIEW`→`CANCELED` 조건부 전이(지금 `CallbackHandler.cancel`의 쿼리), 성공 `STARTED`, 0행 `STALE`.
    - `ConfirmResult retry(Long postId, int version)` — 지금 `CallbackHandler.retry` 분기: 실패 단계 `ANALYZING`이면 분석 실행기에서 `analysisService.analyze(id, FAILED)` 후 `STARTED`, `PUBLISHING`이면 `publishService.retry`, 그 밖 `STALE`.
    - `PostDetailResponse confirmFromWeb(Long id, int version)`, `cancelFromWeb(...)`, `retryFromWeb(...)` — 게시물 없으면 404 `COMMON_NOT_FOUND`, 담당 `TELEGRAM`이면 409 `POST_NOT_EDITABLE`, 버전·상태 불일치 또는 `STALE`이면 409 `POST_VERSION_CONFLICT`, `CAPTION_TOO_LONG`이면 400 `POST_CAPTION_TOO_LONG`, 성공이면 `postQueryService.detail(id)`.
  - 엔드포인트: `POST /api/v1/posts/{id}/confirm|cancel|retry`, 본문 `PostActionRequest`, 200 `ApiResponse<PostDetailResponse>`.

- [ ] **Step 1: 실패하는 테스트 작성** (`PostActionServiceTest`)
  - WEB·PENDING_REVIEW·버전 일치 confirm → `publishService.confirm` 1회, 상세 반환.
  - TELEGRAM 게시물 confirm → `BusinessException(POST_NOT_EDITABLE)`.
  - confirm 결과 `STALE` → `POST_VERSION_CONFLICT`; `CAPTION_TOO_LONG` → `POST_CAPTION_TOO_LONG`.
  - 버전 불일치 cancel → `POST_VERSION_CONFLICT`, 상태 그대로.
  - FAILED(분석 단계) retry → 분석 실행기에 작업 제출; FAILED(게시 단계) retry → `publishService.retry`.
  - `PostControllerTest`: 세 경로가 200 + `data.status`, 본문 `version` 없으면 400 `COMMON_VALIDATION_FAILED`, 서비스 409 전달.
  - `CallbackHandlerTest`·`CallbackCancelIntegrationTest`: 기존 cancel·retry 동작이 그대로(서비스 경유).
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*PostActionServiceTest' --tests '*PostControllerTest'` → 컴파일 실패.
- [ ] **Step 3: 구현** — `PostActionService`에 cancel·retry 로직을 옮기고 `CallbackHandler`는 그 결과로 기존 알림(`notifyCanceled`·`notifyPublishing`·`accepted` 문구)만 붙인다. confirm은 `publishService.confirm` 그대로 쓴다. `*FromWeb`은 읽기 트랜잭션에서 담당·상태·버전을 먼저 확인한 뒤 위 메서드를 부른다(실제 경합은 조건부 전이가 막는다).
- [ ] **Step 4: 실행해 통과 확인** — 위 테스트 + `--tests '*Callback*'` 통과, 전체 `./gradlew test` 실패 0.
- [ ] **Step 5: 커밋** — `Feat(api) : 웹 게시 확인·취소·재시도 API`

### Task 3: 웹 개요 화면과 기록 목록 연결

**Files:**
- Create: `apps/web/app/posts/[id]/page.tsx`, `apps/web/components/post-overview/PostOverview.tsx`, `apps/web/components/post-overview/PostOverview.test.tsx`, `apps/web/hooks/use-post-action.ts`
- Modify: `apps/web/types/api/post.ts`, `apps/web/hooks/use-post.ts`, `apps/web/components/posts/PostList.tsx`, `apps/web/components/posts/PostList.test.tsx`

**Interfaces:**
- Consumes: Task 2 엔드포인트, Task 1 `channel`.
- Produces:
  - `type PostChannel = "TELEGRAM" | "WEB"`; `PostDetailResponse.channel`, `PostSummaryResponse.channel`.
  - `usePost(id: string, options?: { poll?: boolean })` — `poll`이면 `refetchInterval: (q) => ["RECEIVED","ANALYZING","PUBLISHING"].includes(q.state.data?.status ?? "") ? 3000 : false`.
  - `usePostAction(id: string, action: "confirm" | "cancel" | "retry")` — `POST /api/v1/posts/${id}/${action}` 본문 `{version}`; 성공 시 `["posts", id]` 캐시를 응답으로 갱신하고 `["posts"]` 무효화; 409이면 `["posts", id]` 다시 불러오기.
  - `<PostOverview id={string} />`

- [ ] **Step 1: 실패하는 테스트 작성** (`PostOverview.test.tsx`, 기존 테스트처럼 `apiFetch`·`next/navigation` mock)
  - `ANALYZING` → `분석 중입니다`, 버튼 없음.
  - `PENDING_REVIEW` + `WEB` → 사진(`img` 개수 = photos 길이, 순서대로 alt `사진 1`…), 술 이름, 양조장 주소, 별점, 캡션 전체(`captionHead`+`breweryBlock`+`captionTail`) 표시, [확인]·[수정]·[취소]. [수정]은 `/posts/7/edit` 링크.
  - [확인] → `window.confirm` true일 때만 `POST /api/v1/posts/7/confirm` 본문 `{"version":4}`; 진행 중 세 버튼 disabled.
  - confirm이 `ApiError(409, "POST_VERSION_CONFLICT", …)` → 그 메시지 `role="alert"`, 상세 다시 요청.
  - `PENDING_REVIEW` + `TELEGRAM` → `텔레그램에서 진행 중인 게시물입니다.`, [확인]·[취소] 없음, `editable`이면 [수정] 있음.
  - `PUBLISHED` → `permalink` 링크. `FAILED` → `failureReason` + [다시 시도], `editable`이면 [수정]도. `CANCELED` → `취소한 게시물입니다`.
  - `PostList.test.tsx`: 행 링크가 `/posts/{id}`.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 페이지는 `posts/[id]/edit/page.tsx`와 같은 구조(AppNav Suspense + main, 제목 `후기`). 캡션은 `whitespace-pre-wrap`. 렌더 사진은 `photo.url?v=${version}`(수정 화면과 같은 캐시 무효화).
- [ ] **Step 4: 실행해 통과 확인** — `pnpm test`, `pnpm lint` 통과.
- [ ] **Step 5: 커밋** — `Feat(web) : 게시물 개요 화면과 웹 확인·취소`

### Task 4: 공유 화면 확인 모달·사진 빼기·11장 처리

**Files:**
- Modify: `apps/web/components/share/ShareForm.tsx`, `apps/web/components/share/ShareForm.test.tsx`

**Interfaces:**
- Consumes: `useCreatePost` (응답 `PostCreateResponse.id`), `next/navigation` `useRouter`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - 사진 3장: [보내기] → 모달(`role="dialog"`)에 `사진 3장`, 별점(없으면 `없음`), 메모 앞 40자. 모달 [돌아가기] → 전송 없음. 모달 [보내기] → `POST /api/v1/posts` 1회, 성공 후 `router.replace("/posts/12")`.
  - 사진 11장: 별점·메모 입력 disabled, 제출 버튼 텍스트 `사진을 10장 이하로 줄여 주세요 (지금 11장)` + disabled, 이전 빨간 문구 없음. 한 장 [빼기] → 10장, 입력·버튼 정상(`보내기`).
  - 사진 1장: [빼기] disabled.
  - 뺀 사진은 전송 `FormData`의 `photos`에 없다.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 사진 목록은 컴포넌트 상태(공유 캐시는 건드리지 않음). 모달은 네이티브 `<dialog>`(`showModal`; jsdom에 없으면 테스트 setup에서 `HTMLDialogElement.prototype.showModal/close` 폴리필). 접수 완료 문구(`분석이 끝나면 텔레그램으로…`)는 없앤다.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm test`, `pnpm lint`.
- [ ] **Step 5: 커밋** — `Feat(web) : 공유 화면 확인 모달과 사진 빼기`

### Task 5: 수정 화면 저장 이동과 검색 콤보박스

**Files:**
- Modify: `apps/web/components/post-edit/PostEditForm.tsx`, `DrinkPicker.tsx`, `BreweryPicker.tsx`, `PostEditForm.test.tsx`
- Create: `apps/web/components/post-edit/DrinkPicker.test.tsx`, `apps/web/components/post-edit/BreweryPicker.test.tsx`

- [ ] **Step 1: 실패하는 테스트 작성**
  - 저장 성공 → `router.push("/posts/7")`; `텔레그램으로 새 개요` 문구 없음.
  - 술 검색창에서 Enter → PUT 요청 0회. 결과가 있으면 첫 항목이 선택됨(`onChange` 첫 항목 값).
  - 결과 항목 클릭 → 검색어가 비고 결과 목록(`role="listbox"`)이 사라짐, `선택한 술: <이름>` 표시.
  - 다시 검색했을 때 선택 중인 항목은 `aria-selected="true"`.
  - ArrowDown/ArrowUp으로 활성 항목 이동(`aria-activedescendant`), Enter로 그 항목 선택.
  - 양조장 검색도 같은 네 케이스.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 두 피커는 같은 동작이므로 `components/post-edit/SearchCombobox.tsx`(제네릭: `items`, `getKey`, `renderItem`, `selectedKey`, `onPick`, `query`, `onQueryChange`)로 뽑아 둘 다 쓴다. 입력은 `role="combobox"`, `aria-expanded`, `aria-controls`.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm test`, `pnpm lint`.
- [ ] **Step 5: 커밋** — `Fix(web) : 수정 화면 검색 엔터 저장 막고 선택 표시`

### Task 6: 사진 버튼 겹치기와 애니메이션

**Files:**
- Modify: `apps/web/components/post-edit/PhotoOrderList.tsx`, `PhotoOrderList.test.tsx`

- [ ] **Step 1: 실패하는 테스트 작성**
  - 각 사진 `li` 안에서 버튼 4개(`위로`·`아래로`·`삭제`·`여백 넣기`/`여백 빼기`, 접근 이름 유지)가 이미지와 같은 `relative` 컨테이너 안의 오버레이에 있다.
  - [아래로] 클릭 직후 모든 사진 버튼 disabled, 애니메이션 끝(`transitionend` 발생 또는 `vi.advanceTimersByTime(250)`) 뒤 `onChange` 호출되고 버튼 다시 활성.
  - `matchMedia('(prefers-reduced-motion: reduce)')`가 true → 클릭 즉시 `onChange`, 비활성 구간 없음.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 오버레이는 이미지 아래쪽 반투명 띠(`bg-black/50`, 흰 아이콘 버튼, 터치 대상 44px 이상). 이동은 두 `li`에 `translateY`(서로 높이만큼) 200ms transition 후 `onChange`; 삭제는 `opacity`·`scale` 200ms 후 `onChange`; 여백 토글은 이미지 `object-fit` 전환에 200ms transition. 애니메이션 안전장치로 250ms 타이머 후에도 끝낸다.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm test`, `pnpm lint`.
- [ ] **Step 5: 커밋** — `Feat(web) : 사진 버튼 겹치기와 이동 애니메이션`

### Task 7: 로그아웃

**Files:**
- Modify: `apps/web/components/layout/AppNav.tsx`, `apps/web/components/layout/AppNav.test.tsx`
- Create: `apps/web/hooks/use-logout.ts`

**Interfaces:**
- Produces: `useLogout()` — `DELETE /api/v1/sessions`, 성공·실패 모두 `queryClient.clear()` 후 `router.replace("/login")`.

- [ ] **Step 1: 실패하는 테스트 작성** — `AppNav`에 [로그아웃] 버튼; 클릭 → `DELETE /api/v1/sessions` 1회, `router.replace("/login")`. API가 실패해도 이동.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 버튼은 메뉴 오른쪽 끝(`ml-auto`), 링크와 같은 크기의 텍스트 버튼.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm test`, `pnpm lint`.
- [ ] **Step 5: 커밋** — `Feat(web) : 메뉴에 로그아웃 추가`

### Task 8: 문서

**Files:**
- Modify: `docs/telegram-messages.md`, `docs/superpowers/specs/2026-11-14-review-to-instagram-design.md`, `docs/superpowers/plans/2026-11-18-review-to-instagram-p4-web-ops.md`

- [ ] **Step 1:** `docs/telegram-messages.md` 앞부분에 "담당 채널" 절: WEB 게시물은 어떤 메시지도 받지 않음, 웹 저장으로 넘어가면 이전 개요 버튼만 지움, WEB 게시물의 옛 버튼은 stale 응답.
- [ ] **Step 2:** 기존 설계 "게시 전 확인" 표 아래 `- 변경 (2026-11-18): 웹 담당 게시물은 웹 개요 화면에서 확인한다. 2026-11-18-web-channel-design.md` 한 줄.
- [ ] **Step 3:** P4 계획 "검증 결과 (Task 9) / Step 2"에 표로 기록: 1 미확인, 2 텔레그램 대신 웹 진행 요청 → 이 계획, 3 로그아웃 없음 → Task 7, 4 통과(개선 요청 → Task 4), 5 웹 진행 요청·검색 엔터 결함·콤보박스·사진 버튼 → Task 5·6, 6 통과, 7 통과(`지금은 수정할 수 없는 게시물입니다.` 확인), 8 통과.
- [ ] **Step 4: 커밋** — `Docs : 담당 채널 규칙과 P4 검증 결과 기록`
