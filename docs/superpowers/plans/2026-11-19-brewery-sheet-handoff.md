# 양조장 시트와 텔레그램 안내 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 양조장 정보를 게시물 수정 화면 위 시트에서 고쳐 바로 저장하고, 그 양조장을 쓰는 검토 대기 게시물의 캡션을 서버가 갱신하며, 웹 담당으로 넘어간 게시물에는 텔레그램 안내 메시지를 보낸다.

**Architecture:** `BreweryService.update`가 `BreweryUpdatedEvent`를 발행하고, post 쪽 리스너(`BreweryCaptionSync`)가 같은 트랜잭션에서 고칠 수 있는 게시물의 양조장 블록을 다시 그리고 버전을 올리며 텔레그램 담당이면 웹 담당으로 넘긴다. 웹 담당 전환은 `PostHandedOverEvent` 하나로 모아, 커밋 뒤 `PostNotifier.handOverToWeb`이 버튼 제거와 안내 메시지 전송을 한다. 웹은 수정 화면의 양조장 칸을 지우고 `BreweryForm`을 재사용한 바텀 시트를 연다.

**Tech Stack:** Spring Boot / JPA / JUnit (apps/api), Next.js 16 / React 19 / React Query / Vitest (apps/web)

**Spec:** `docs/superpowers/specs/2026-11-19-web-redesign-design.md` (1단계)

## Global Constraints

- 브랜치: `dev`에서 `feat/brewery-sheet-handoff`를 딴다. 피처 브랜치는 PR 없이 로컬 머지.
- 커밋 전 `.gitmessage.txt`를 읽고 `Type(scope) : 제목` 형식(예: `Feat(api) : 양조장 수정 시 대기 캡션 갱신`), 제목 50자 이내. 마지막 줄 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- 커밋 전 바뀐 앱의 `lint`·`test` 통과 (`apps/web`: `pnpm lint`, `pnpm test` / `apps/api`: `./gradlew test`). context-mode 훅이 Bash의 `./gradlew`를 막으면 `ctx_execute`(shell)로 실행하고 결과는 `build/test-results/test/*.xml`로 센다.
- 안내 메시지 문구(정확히): 첫 줄 `검토와 업로드는 k-drinker에서 이어서 진행해 주세요.`, 둘째 줄 `<a href="{공개 주소}/posts/{id}">웹에서 열기</a>`. 느낌표·줄표 문자 금지.
- 중복 키: `handoff:{postId}`. NotificationKind는 `HANDOFF` (DB `kind`는 VARCHAR(40)이라 마이그레이션 불필요).
- 텔레그램 문구를 바꾸면 `docs/telegram-messages.md`를 같은 커밋에서 고친다.
- 웹: Tailwind만, 인라인 `style={}` 금지, `div onClick` 금지, API 호출은 `lib/api.ts` 래퍼, pnpm만.
- 고칠 수 있는 게시물 = `PostQueryService.editable(post)` (검토 대기, 또는 `igContainerId == null`인 실패).

## Review Focus

- 양조장을 고칠 때 캡션 마커가 2개가 아닌 게시물(사용자가 마커를 지움) → 그 게시물 캡션은 건드리지 않지만, 텔레그램 담당이면 담당 전환·안내는 한다. Task 2에 테스트.
- 같은 양조장을 쓰는 게시물이 여럿이고 그중 일부만 텔레그램 담당 → 텔레그램 담당인 것만 `PostHandedOverEvent`. Task 2에 테스트.
- 이미 웹 담당인 게시물을 수정 화면에서 저장 → 안내 메시지를 보내지 않는다(이벤트 없음). Task 3에 테스트.
- 이전 개요 메시지가 없는(전송 실패) 텔레그램 게시물 → 답장 없이 안내만 보낸다. Task 3에 테스트.
- 시트에서 양조장 이름을 비워 저장 → 기존 검증(400)으로 시트에 오류가 보이고 시트는 열린 채 남는다. Task 5에 테스트.

---

### Task 1: 양조장 수정 이벤트와 게시물 조회

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/brewery/event/BreweryUpdatedEvent.java`
- Modify: `apps/api/src/main/java/com/kdrinker/brewery/service/BreweryService.java` (`update`)
- Modify: `apps/api/src/main/java/com/kdrinker/post/repository/PostRepository.java`
- Test: `apps/api/src/test/java/com/kdrinker/brewery/service/BreweryServiceTest.java`, `apps/api/src/test/java/com/kdrinker/post/repository/PostRepositoryTest.java`

**Interfaces:**
- Produces: `record BreweryUpdatedEvent(Long breweryId)`; `BreweryService.update`가 저장 뒤 `ApplicationEventPublisher.publishEvent(new BreweryUpdatedEvent(id))`.
- Produces: `PostRepository.findEditableIdsByBreweryId(Long breweryId): List<Long>` — `p.drink.brewery.id = :breweryId and p.caption is not null and (p.status = PENDING_REVIEW or (p.status = FAILED and p.igContainerId is null))`, id 오름차순.

- [ ] **Step 1: 실패하는 테스트**
  - `BreweryServiceTest.update_publishesBreweryUpdatedEvent`: update 후 발행된 이벤트가 `new BreweryUpdatedEvent(id)` 하나.
  - `BreweryServiceTest.create_publishesNothing`: create는 이벤트를 발행하지 않는다.
  - `PostRepositoryTest.findEditableIdsByBreweryId_returnsOnlyEditableWithCaption`: 같은 양조장의 게시물 5개(검토 대기+캡션, 실패+컨테이너 없음+캡션, 실패+컨테이너 있음, 게시됨, 검토 대기+캡션 null)와 다른 양조장 게시물 1개 → 앞의 두 id만.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*BreweryServiceTest' --tests '*PostRepositoryTest'` → FAIL
- [ ] **Step 3: 구현** — 이벤트 레코드, `update`에서 발행, JPQL 조회 메서드.
- [ ] **Step 4: 실행해 통과 확인** — 같은 명령 → PASS
- [ ] **Step 5: 커밋** — `Feat(api) : 양조장 수정 이벤트와 대상 게시물 조회`

### Task 2: 대기 게시물 캡션 갱신과 웹 담당 전환

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/post/event/PostHandedOverEvent.java`
- Create: `apps/api/src/main/java/com/kdrinker/post/service/BreweryCaptionSync.java`
- Test: `apps/api/src/test/java/com/kdrinker/post/service/BreweryCaptionSyncTest.java` (통합: `@SpringBootTest` + 실제 DB, 기존 `PostEditIntegrationTest` 설정을 따른다)

**Interfaces:**
- Consumes: `BreweryUpdatedEvent`, `PostRepository.findEditableIdsByBreweryId`, `PostRepository.transition(id, from, to, expectedVersion, now)` (from == to로 버전만 올림), `CaptionParts.split/join`, `Post.moveToWeb()`, `Post.replaceCaption(caption, hashtags, edited)`.
- Produces: `record PostHandedOverEvent(Long postId)`; `@Component BreweryCaptionSync` with `@EventListener void onBreweryUpdated(BreweryUpdatedEvent)` — 발행 트랜잭션 안에서 동기 실행.

- [ ] **Step 1: 실패하는 테스트** (`BreweryCaptionSyncTest`)
  - `updatesBreweryBlockOfPendingPost`: 검토 대기 게시물(캡션에 옛 주소) → 양조장 주소 수정 → 저장된 캡션의 마커 사이가 새 주소, 머리·꼬리·`captionEdited` 그대로, 버전 +1.
  - `handsTelegramPostOverToWeb`: 텔레그램 담당 게시물 → 채널 `WEB`, `PostHandedOverEvent(postId)` 1회 (`@RecordApplicationEvents`).
  - `webPostGetsNoHandoffEvent`: 이미 웹 담당 → 캡션은 갱신, 이벤트 없음.
  - `skipsCaptionWithBrokenMarkers`: 마커 1개인 캡션 → 캡션 그대로, 텔레그램 담당이면 그래도 `WEB` + 이벤트.
  - `ignoresPublishedPost`: 게시된 게시물 → 캡션·버전·채널 그대로.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*BreweryCaptionSyncTest'` → FAIL
- [ ] **Step 3: 구현** — 대상 id마다: 게시물을 읽고, 마커가 정확히 2개면 `CaptionParts.split(caption).join(brewery)`로 캡션 교체(`replaceCaption(new, post.getHashtags(), post.isCaptionEdited())`), 텔레그램 담당이면 `moveToWeb()` 후 이벤트 발행, 마지막에 `transition(id, status, status, version, now)`로 버전을 올린다. `transition`이 영속성 컨텍스트를 비우므로(clearAutomatically) 엔티티 변경 뒤에 호출한다. 마커 개수 판정은 `CaptionParts`에 `static boolean hasBreweryBlock(String stored)`(정확히 2개)로 둔다.
- [ ] **Step 4: 실행해 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `Feat(api) : 양조장 수정 시 대기 게시물 캡션 갱신`

### Task 3: 웹 담당 전환 안내 메시지

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/dto/TelegramText.java` (답장 대상 추가)
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/TelegramClient.java` (`sendMessage`)
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/MessageFormatter.java`
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/PostNotifier.java`
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/PostNotificationListener.java`
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/entity/NotificationKind.java` (`HANDOFF`)
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostEditService.java` (저장 시 `PostHandedOverEvent` 발행)
- Modify: `docs/telegram-messages.md`
- Test: `TelegramClientTest`, `MessageFormatterTest`, `PostNotifierTest`, `PostNotificationListenerTest`, `PostEditServiceTest`

**Interfaces:**
- Consumes: `PostHandedOverEvent(postId)` (Task 2), `TelegramSender.send(key, kind, postId, text)`, `TelegramSender.messageIds(postId, OVERVIEW)`.
- Produces: `TelegramText(String html, List<List<InlineButton>> keyboard, Long replyToMessageId)` + `TelegramText withReplyTo(Long messageId)`; 기존 `TelegramText.of(html)`와 2인자 생성 경로는 `replyToMessageId = null`로 유지.
- Produces: `MessageFormatter.handoff(String url): TelegramText`.
- Produces: `@Async PostNotifier.handOverToWeb(Long postId)` — `removeOverviewButtons` 로직 + 안내 전송. 기존 `removeOverviewButtons`는 이 메서드 안으로 합치거나 private으로 내린다.
- `PostEditedEvent`의 `handedOverToWeb` 필드는 없애고, 저장으로 텔레그램 → 웹이 되면 `PostEditService`가 같은 트랜잭션에서 `PostHandedOverEvent`를 발행한다. 리스너는 `@TransactionalEventListener(AFTER_COMMIT) onHandedOver(PostHandedOverEvent)` 하나로 `notifier.handOverToWeb` 호출.

- [ ] **Step 1: 실패하는 테스트**
  - `TelegramClientTest.sendMessage_withReplyTo_sendsReplyParameters`: 본문에 `"reply_parameters": {"message_id": 77, "allow_sending_without_reply": true}`. 답장 대상이 없으면 키 자체가 없다.
  - `MessageFormatterTest.handoff`: html이 정확히 `검토와 업로드는 k-drinker에서 이어서 진행해 주세요.\n<a href="https://k.example/posts/12">웹에서 열기</a>`, 키보드 없음. URL의 `"`는 이스케이프(`escapeAttr`).
  - `PostNotifierTest.handOverToWeb_removesButtonsAndRepliesToLatestOverview`: OVERVIEW 메시지 id [10, 11] → 버튼 제거 후 `send("handoff:5", HANDOFF, 5, text)`의 `replyToMessageId == 11`.
  - `PostNotifierTest.handOverToWeb_withoutOverview_sendsWithoutReply`: OVERVIEW 없음 → `replyToMessageId == null`로 전송.
  - `PostNotificationListenerTest.onHandedOver_callsNotifier`.
  - `PostEditServiceTest`: 텔레그램 담당 저장 → `PostHandedOverEvent` 1회, 웹 담당 저장 → 0회.
- [ ] **Step 2: 실행해 실패 확인** — 위 테스트 클래스들 → FAIL
- [ ] **Step 3: 구현** — 위 Interfaces대로. 공개 주소는 `CallbackHandler`처럼 `@Value("${app.public-base-url}")`를 받아 끝 `/`를 뗀다.
- [ ] **Step 4: `docs/telegram-messages.md` 갱신** — "담당 채널" 절 15행과 버튼 제거 규칙(32행), 메시지 3·5의 "새 개요는 보내지 않는다" 문장에 안내 메시지를 더하고, 메시지 목록에 새 절 "웹 담당 전환 안내 · HANDOFF"(언제: 담당이 `WEB`으로 넘어갈 때 — 웹 저장 또는 양조장 수정, 중복 키 `handoff:{postId}`, 소스 `MessageFormatter.handoff(String url)`, 이전 개요에 답장, 템플릿)를 추가한다.
- [ ] **Step 5: 실행해 통과 확인** — `./gradlew test` 전체 → PASS
- [ ] **Step 6: 커밋** — `Feat(api) : 웹 담당 전환 시 텔레그램 안내 전송`

### Task 4: 게시물 수정 요청에서 `breweryEdit` 제거

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/post/dto/PostUpdateRequest.java`, `apps/api/src/main/java/com/kdrinker/post/service/PostEditService.java`
- Modify: `apps/web/types/api/post.ts` (`breweryEdit` 필드 삭제)
- Test: `PostEditServiceTest`, `PostEditIntegrationTest`, `PostControllerTest` 의 `breweryEdit` 관련 테스트 삭제·수정

**Interfaces:**
- Produces: `PostUpdateRequest`에 `breweryEdit` 없음. 웹 `PostUpdateRequest` 타입에도 없음. Task 5가 이 타입을 쓴다.

결정: 필드를 지운다 (스펙의 빌더 재량). 웹이 보내지 않는 경로를 남기면 양조장을 고치는 길이 둘이 된다.

- [ ] **Step 1: `breweryEdit`를 쓰는 테스트를 지우거나, 다른 술로 바꾼 저장이 양조장 레코드를 바꾸지 않는다는 테스트(`update_doesNotTouchBreweryRecord`)로 바꾼다.**
- [ ] **Step 2: 필드와 `PostEditService`의 관련 검증(이름 일치 검사, `applyEdit` 호출, `identityChanged`의 `breweryEdit` 조건)을 지운다. `BreweryService.applyEdit`의 다른 호출이 없으면 private으로 내린다.**
- [ ] **Step 3: 실행해 통과 확인** — `./gradlew test` → PASS. 웹은 Task 5에서 컴파일을 맞춘다(같은 커밋).
- [ ] **Step 4: Task 5와 함께 커밋**

### Task 5: 웹 양조장 시트

**Files:**
- Create: `apps/web/components/post-edit/BrewerySheet.tsx`
- Create: `apps/web/components/post-edit/BrewerySheet.test.tsx`
- Modify: `apps/web/components/breweries/BreweryForm.tsx` (`onSaved` prop)
- Modify: `apps/web/components/post-edit/BreweryPicker.tsx` (정보 칸·`fields`·`onFieldsChange` 삭제, "양조장 정보 고치기" 버튼)
- Modify: `apps/web/components/post-edit/PostEditForm.tsx` (`edits`·`fieldsOf`·`breweryEdit` 조립 삭제)
- Modify: `apps/web/hooks/use-breweries.ts` (`useSaveBrewery` 성공 시 `["posts"]`도 무효화)
- Test: `BreweryPicker.test.tsx`, `PostEditForm.test.tsx`, `BreweryForm.test.tsx`

**Interfaces:**
- Consumes: 웹 `PostUpdateRequest`(Task 4, `breweryEdit` 없음), `useSaveBrewery(id)`, `useBrewery(id)`.
- Produces: `BreweryForm({ brewery, onSaved }: { brewery?: BreweryResponse; onSaved?: (saved: BreweryResponse) => void })` — `onSaved`가 있으면 저장 성공 시 호출하고, 없으면 지금 동작(저장 완료 표시) 그대로.
- Produces: `BrewerySheet({ brewery, onClose }: { brewery: BreweryResponse; onClose: () => void })` — `role="dialog"`, `aria-modal="true"`, 제목 "양조장 정보", 안내 문구 정확히 `이 양조장을 쓰는 다른 게시물에도 반영돼요`, 안에 `BreweryForm`(onSaved → onClose), [닫기] 버튼, Esc로 닫힘, 열릴 때 첫 입력칸에 포커스, 닫히면 여는 버튼으로 포커스 복귀. 기존 `ConfirmDialog`의 포커스 처리 방식을 따른다. 모양은 아래 고정 패널(3단계에서 디자인 토큰으로 다시 입힌다).
- `BreweryPicker`: `brewery`가 있으면 그 아래 `<button type="button">양조장 정보 고치기</button>`. 누르면 시트를 연다. 대상은 지금 화면의 `base`(새로 고른 양조장 → 새 술의 양조장 → 저장된 양조장).

- [ ] **Step 1: 실패하는 테스트**
  - `BrewerySheet.test.tsx`: 열면 다이얼로그 이름 "양조장 정보", 안내 문구 표시, 첫 입력칸 포커스 / 저장 성공(`fetch` 목) → `onClose` 호출 / 이름을 비워 저장해 400 응답 → 필드 오류가 보이고 `onClose` 안 불림 / Esc → `onClose`.
  - `BreweryPicker.test.tsx`: 주소·전화 입력칸이 없다 / 양조장이 있으면 "양조장 정보 고치기" 버튼이 있고 누르면 다이얼로그가 열린다 / 양조장이 없으면 버튼이 없다.
  - `PostEditForm.test.tsx`: 저장 요청 본문에 `breweryEdit` 키가 없다 / 술을 바꾼 상태에서 시트를 열었다 닫아도 술 선택·메모 입력이 그대로다.
  - `BreweryForm.test.tsx`: `onSaved`가 있으면 저장 성공 시 저장된 양조장으로 호출된다.
- [ ] **Step 2: 실행해 실패 확인** — `cd apps/web && pnpm test` → FAIL
- [ ] **Step 3: 구현** — Interfaces대로. 지운 상태·함수(`edits`, `fieldsOf`, `orNull`의 양조장 용도, `handleFieldsChange`)를 정리한다.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm lint && pnpm test` → 통과, `cd apps/api && ./gradlew test` → 통과
- [ ] **Step 5: 커밋 (Task 4 포함)** — `Feat : 양조장 정보를 수정 화면 시트에서 고치기`
