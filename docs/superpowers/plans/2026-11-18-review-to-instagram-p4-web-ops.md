# 전통주 후기 → 인스타그램 P4 웹·운영 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 안드로이드 갤러리 공유로 게시물을 접수하고, 텔레그램 [수정] 링크로 여는 웹 수정 화면에서 고쳐 저장하면 새 개요가 오게 한다. 기록 목록·양조장 관리·토큰 다시 불러오기 화면을 붙이고, 취소 사진 정리와 Claude 사용량 로그로 운영을 마무리한다.

**Architecture:** API에 조회(`GET /posts`, `GET /posts/{id}`)·수정 저장(`PUT /posts/{id}`)·술/양조장 검색·관리 엔드포인트를 더한다. 수정 저장은 `AnalysisService`처럼 Claude 캡션 재생성을 트랜잭션 밖에서 하고, DB 쓰기는 짧은 트랜잭션에서 버전 검사 전이(`→ PENDING_REVIEW`)로 끝낸 뒤 `PostEditedEvent`로 새 개요를 보낸다. 웹은 PWA manifest `share_target`(POST multipart)을 서비스 워커가 받아 사진을 브라우저 Cache에 두고 `/share` 화면으로 넘긴다. 로그인이 풀려 있으면 로그인 후 같은 사진으로 이어서 보낸다(서버에는 로그인 전에 아무것도 올리지 않는다).

**Tech Stack:** Spring Boot 4.1.2 / Java 17 / JPA / Flyway, Next.js 16.4 (App Router, `cacheComponents`) / React 19 / React Query 5 / Tailwind 4 / Vitest, Service Worker + Cache API.

**Spec:** `docs/superpowers/specs/2026-11-14-review-to-instagram-design.md` (T8, T9). 텔레그램 문구는 `docs/telegram-messages.md` (메시지 3 개요, 5 수정 링크), 캡션 마커는 `docs/instagram-caption.md`.

| 단계 | 태스크 | 내용 |
|---|---|---|
| P1 기반 | T1~T3 | 완료 |
| P2 분석 | T4, T5 | 완료 |
| P3 텔레그램·게시 | T6, T7 | 완료 |
| **P4 웹·운영** | **T8, T9** | **공유 입력, 수정 화면, 기록, 양조장 관리, 토큰 다시 불러오기, 사진 정리, Claude 사용량 로그** |

## Global Constraints

- 경로 약어: 자바 경로는 `apps/api/src/main/java/com/kdrinker/`(테스트 `src/test/java/com/kdrinker/`) 아래, 웹 경로는 `apps/web/` 아래다.
- 응답·에러·페이징은 `api-conventions` 스킬 그대로: 페이징 `?page=0&size=20`(size 최대 100), `data: { items, page, size, totalElements, totalPages }`. 수정은 200, 생성은 201, 충돌은 409. 새 에러 코드는 `ErrorCode` 한 곳에 `<DOMAIN>_<REASON>`으로, 메시지는 한국어 완성 문장.
- 엔티티는 컨트롤러 밖으로 내보내지 않는다. DTO는 `record`, 요청 `XxxRequest`/`XxxCreateRequest`, 응답 `XxxResponse`. 웹 타입은 `types/api/`에 같은 이름으로 둔다.
- 상태 변경은 `PostRepository.transition(id, from, to, expectedVersion, now)`로만. 다른 필드는 transition 전에 고치고 transition 후 다시 읽는다 (`.claude/rules/api.md`).
- Claude 호출은 트랜잭션 밖. DB 쓰기는 `TransactionTemplate`으로 짧게 (`AnalysisService`와 같은 이유).
- 미디어 파일 삭제·덮어쓰기는 커밋 뒤(`afterCommit`). 커밋 전에 공개 파일을 건드리지 않는다.
- 웹: 데이터 호출은 `lib/api.ts`의 `apiFetch`만, 클라이언트 React Query로. `AUTH_REQUIRED`는 기존 `QueryProvider`가 `/login?next=<현재 경로>`로 보낸다(새 처리 만들지 않음). Tailwind만, 인라인 `style` 금지, `div onClick` 금지. 휴대폰 화면 우선, 최소한의 디자인.
- 도메인 로직(캡션 분할·조립, 글자 수 검사, 양조장 반영 규칙)은 API에만. 웹은 받은 값을 표시하고 보낸다.
- 새 환경변수는 만들지 않는다. 정리 주기 같은 값은 `application.yml`의 `app.*` 고정값.
- 텔레그램 문구는 바꾸지 않는다. 새 개요는 기존 `PostNotifier.notifyReviewReady`가 보낸다(이전 개요 버튼 제거 포함).
- 테스트: api `./gradlew test`(Bash 훅이 막으면 `ctx_execute` shell로 실행하고 `build/test-results/test/*.xml`로 센다), web `pnpm test`·`pnpm lint`. 서비스는 Mockito, 컨트롤러는 `@WebMvcTest`, 리포지토리는 `@DataJpaTest`. 테스트 이름은 행동 서술, given/when/then.

## Review Focus

1. **[수정] 저장과 텔레그램 [확인]이 동시에 일어남**: 둘 중 하나만 이긴다. [확인]이 먼저면 저장은 409(`POST_VERSION_CONFLICT`)이고, 저장이 먼저면 이전 개요의 [확인]은 오래된 버튼 팝업이 뜬다 → Task 3 `저장_직전에_버전이_바뀌면_409이고_아무것도_바뀌지_않는다`.
2. **게시 단계 실패(컨테이너가 이미 만들어짐) 뒤 수정**: 이미 인스타그램에 올라갔을 수 있는 게시물을 고쳐 다시 올리면 두 번 게시된다. `igContainerId`가 있는 FAILED는 수정을 막고 [재시도]로만 처리한다 → Task 3 `컨테이너가_있는_실패_게시물은_수정할_수_없다`.
3. **캡션을 직접 고친 뒤 양조장을 바꿈**: 고친 문장은 그대로 두고 양조장 블록만 바뀐다. Claude 재생성이 실패해도 저장은 되고 블록만 바뀐다 → Task 3 `직접_고친_캡션은_양조장_블록만_바뀐다`, `재생성이_실패하면_양조장_블록만_바꿔_저장한다`.
4. **로그인이 풀린 휴대폰에서 공유**: 로그인 화면을 거쳐 `/share`로 돌아오면 고른 사진이 그대로 있고, 보낸 뒤에는 브라우저 Cache에서 지워진다 → Task 5 `keeps shared photos until sent`, `clears shared photos after sending`.
5. **사진을 모두 지우고 저장 / 다른 게시물의 사진 id를 섞어 보냄**: 400이고 아무것도 지워지지 않는다 → Task 3 `사진을_모두_빼면_400이다`, `다른_게시물의_사진_id는_400이다`.

---

### Task 1: 게시물 조회 API와 캡션 분할

**Files:**
- Create: `caption/CaptionParts.java`, `post/service/PostQueryService.java`, `post/dto/PostSummaryResponse.java`, `post/dto/PostDetailResponse.java`, `post/dto/PostPhotoResponse.java`, `common/PageResponse.java`, `brewery/dto/BreweryResponse.java`
- Modify: `post/controller/PostController.java`, `post/repository/PostRepository.java`
- Test: `caption/CaptionPartsTest.java`, `post/service/PostQueryServiceTest.java`, `post/controller/PostControllerTest.java`

**Interfaces:**
- Produces:
  - `record CaptionParts(String head, String tail)` — 저장된 캡션의 양조장 블록 앞(훅·소개·맛·별점)과 뒤(해시태그). `static CaptionParts split(String stored)`: 첫 `BreweryBlock.MARKER` 앞을 head, 마지막 MARKER 뒤를 tail로, 둘 다 `strip()`. 마커가 없으면(옛 데이터) head=전체, tail="". `stored`가 null이면 `("", "")`. `String join(Brewery b)`: `head`, `BreweryBlock.render(b)`, `tail` 중 빈 것을 빼고 `"\n\n"`로 잇는다(블록은 항상 넣는다, `CaptionAssembler.assemble`과 같은 모양).
  - `record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages)`, `static <T> PageResponse<T> from(Page<T> page)`.
  - `record BreweryResponse(Long id, String name, String address, String phone, String homepage, String instagramHandle, String otherSns, String source, boolean verified)`, `static from(Brewery)`.
  - `record PostSummaryResponse(Long id, String status, String drinkName, BigDecimal rating, String thumbnailUrl, String permalink, Instant createdAt)` — thumbnail은 position 0 사진의 `PhotoService.publicUrl`, 사진이 없으면 null.
  - `record PostPhotoResponse(Long id, int position, String url, String fitMode)`.
  - `record PostDetailResponse(Long id, String status, int version, boolean editable, String failedStage, String failureReason, Long drinkId, String drinkName, BreweryResponse brewery, BigDecimal rating, String memo, String captionHead, String breweryBlock, String captionTail, boolean captionEdited, int captionLength, List<PostPhotoResponse> photos, String permalink)` — `breweryBlock`은 `BreweryBlock.render(brewery)`에서 마커 줄을 뺀 표시용 텍스트, `captionLength`는 `CaptionAssembler.length(caption)`(캡션 없으면 0), `editable`은 `PostQueryService.editable(Post)`(정적 메서드, Task 3의 저장 검사도 이것을 쓴다): 상태가 `PENDING_REVIEW`, 또는 `FAILED`이면서 `igContainerId == null`.
  - `GET /api/v1/posts?page=0&size=20` → `ApiResponse<PageResponse<PostSummaryResponse>>`, id 내림차순. size 1~100 밖은 400.
  - `GET /api/v1/posts/{id}` → `ApiResponse<PostDetailResponse>`, 없으면 404 `COMMON_NOT_FOUND`.
  - `PostRepository`: `@EntityGraph(attributePaths = {"drink", "drink.brewery"}) Page<Post> findAllByOrderByIdDesc(Pageable)`.

- [ ] **Step 1: 실패하는 테스트 작성** — `CaptionPartsTest`: `마커_앞뒤로_나눈다`(head="훅\n\n소개\n\n★4.5", tail="#막걸리 #전통주"), `마커가_없으면_전체가_머리다`, `null은_빈_두_조각이다`, `빈_조각은_빼고_블록과_잇는다`(head="", tail="" → `BreweryBlock.render(null)`만), `나눈_뒤_다시_이으면_원래_캡션이다`(`CaptionAssembler.assemble` 결과로 왕복). `PostQueryServiceTest`: `상세는_양조장_블록을_마커_없이_준다`, `컨테이너가_있는_실패는_편집_불가다`, `사진이_없으면_썸네일은_null이다`. `PostControllerTest`: `목록은_페이지_형식이다`(JSON 경로 `$.data.items[0].id`, `$.data.totalPages`), `size_101은_400이다`, `없는_게시물은_404다`.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*CaptionPartsTest' --tests '*PostQueryServiceTest' --tests '*PostControllerTest'` → 컴파일 실패.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로. `PostQueryService`는 `@Transactional(readOnly = true)`, 사진은 `PostPhotoRepository.findByPostIdOrderByPositionAsc`. 목록 썸네일은 게시물마다 한 번 조회해도 된다(size ≤ 100, 1인 사용).
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS, 이어 `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 게시물 목록·상세 조회 API`

### Task 2: 술 검색과 양조장 관리 API

**Files:**
- Create: `drink/controller/DrinkController.java`, `drink/dto/DrinkResponse.java`, `drink/service/DrinkQueryService.java`, `brewery/controller/BreweryController.java`, `brewery/dto/BreweryRequest.java`, `brewery/service/BreweryService.java`
- Modify: `brewery/entity/Brewery.java`, `brewery/repository/BreweryRepository.java`, `drink/repository/DrinkRepository.java`
- Test: `brewery/service/BreweryServiceTest.java`, `brewery/controller/BreweryControllerTest.java`, `drink/controller/DrinkControllerTest.java`, `brewery/entity/BreweryManualEditTest.java`

**Interfaces:**
- Consumes: `BreweryResponse`, `PageResponse` (Task 1), `Names.normalize`, `InstagramHandles.normalize`.
- Produces:
  - `record DrinkResponse(Long id, String name, Long breweryId, String breweryName)`. `GET /api/v1/drinks?q=` → `ApiResponse<List<DrinkResponse>>`, 정규화 이름에 `Names.normalize(q)`가 들어 있는 술 최대 20개(이름순). q가 비면 빈 배열. `DrinkRepository.findTop20ByNormalizedNameContainingOrderByNameAsc(String)`.
  - `GET /api/v1/breweries?q=&page=0&size=20` → `ApiResponse<PageResponse<BreweryResponse>>` (q 비면 전체, 이름순; 있으면 `normalizedName` 포함 검색). `GET /api/v1/breweries/{id}`, `POST /api/v1/breweries`(201), `PUT /api/v1/breweries/{id}`(200). 모두 `BreweryResponse`.
  - `record BreweryRequest(@NotBlank @Size(max = 255) String name, @Size(max = 255) String address, @Size(max = 255) String phone, @Size(max = 1000) String homepage, @Size(max = 255) String instagramHandle, @Size(max = 255) String otherSns)`.
  - `Brewery.editManually(String name, String normalizedName, String address, String phone, String homepage, String instagramHandle, String otherSns)`: 값을 그대로 덮어쓰고(빈 문자열은 null), `source = MANUAL`, `verified = true`, `sourceUrl`·`webFields`·`webSourceUrl`을 null로(사용자가 본 값이므로 웹 출처 표시를 지운다).
  - `BreweryService.update(Long id, BreweryRequest)`, `create(BreweryRequest)`: 인스타그램 핸들은 `InstagramHandles.normalize`, 입력이 있는데 결과가 null이면 400 `BREWERY_INVALID_INSTAGRAM`("인스타그램 계정 형식이 올바르지 않습니다."). Task 3이 `BreweryService.applyEdit(Brewery, BreweryRequest)`(같은 규칙, 엔티티를 받는 쓰기 메서드)를 쓴다.
- [ ] **Step 1: 실패하는 테스트 작성** — `BreweryManualEditTest`: `직접_고치면_MANUAL이고_검증됨이다`, `직접_고치면_웹_출처가_지워진다`(`webSourceOf("address")`가 비어 있음). `BreweryServiceTest`: `핸들은_골뱅이와_URL을_뗀다`(`"https://instagram.com/hwayo_official/"` → `hwayo_official`), `잘못된_핸들은_400이다`, `없는_양조장은_404다`. `BreweryControllerTest`: `이름이_비면_400과_fields다`, `생성은_201이다`, `검색어로_이름을_찾는다`. `DrinkControllerTest`: `검색어가_비면_빈_배열이다`, `정규화해서_찾는다`("화요 25" → 서비스에 "화요25" 전달).
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*Brewery*' --tests '*DrinkControllerTest'` → 컴파일 실패.
- [ ] **Step 3: 구현** — Interfaces 그대로. 이름을 바꾸면 `normalizedName = Names.normalize(name)`. 정규화 결과가 비면 400 `COMMON_VALIDATION_FAILED`.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS, `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 술 검색·양조장 관리 API`

### Task 3: 게시물 수정 저장 API

**Files:**
- Create: `post/service/PostEditService.java`, `post/dto/PostUpdateRequest.java`, `post/event/PostEditedEvent.java`
- Modify: `post/controller/PostController.java`, `post/entity/Post.java`, `post/entity/PostPhoto.java`, `drink/entity/Drink.java`, `common/ErrorCode.java`, `telegram/PostNotificationListener.java`
- Test: `post/service/PostEditServiceTest.java`, `post/PostEditIntegrationTest.java`(`@SpringBootTest`, H2), `post/controller/PostControllerTest.java`, `telegram/PostNotificationListenerTest.java`(없으면 생성)

**Interfaces:**
- Consumes: `CaptionParts`, `PostQueryService.editable(Post)`, `PostDetailResponse` (Task 1), `BreweryService.applyEdit`, `BreweryRequest` (Task 2), `CaptionService.compose(CaptionInput)`, `ClaudeUnavailableException`, `PhotoService.rerender(photoId, FocusBox, FitMode)`, `PhotoStorage.deleteQuietly`, `PostIntakeService.validate`(별점·메모 규칙 재사용), `PostNotifier.notifyReviewReady(Long)`.
- Produces:
  - `PUT /api/v1/posts/{id}` body `PostUpdateRequest` → 200 `ApiResponse<PostDetailResponse>`(저장 뒤 다시 읽은 값).
  - `record PostUpdateRequest(@NotNull Integer version, Long drinkId, @Size(max = 255) String newDrinkName, Long breweryId, @Valid BreweryRequest breweryEdit, BigDecimal rating, @Size(max = 2000) String memo, @NotNull String captionHead, @NotNull String captionTail, @NotNull List<@Valid PhotoEdit> photos)`, `record PhotoEdit(@NotNull Long id, @NotNull FitMode fitMode)`. `drinkId`와 `newDrinkName`은 둘 중 하나(둘 다 있으면 400). 둘 다 없으면 술을 바꾸지 않는다. `photos` 순서가 새 순서, 빠진 사진은 삭제. `breweryEdit`는 `breweryId`가 가리키는(없으면 현재) 양조장에 적용.
  - `record PostEditedEvent(Long postId)` — 저장 트랜잭션 안에서 발행, `PostNotificationListener`가 `@TransactionalEventListener(AFTER_COMMIT)`으로 받아 `notifier.notifyReviewReady(id)`.
  - 새 `ErrorCode`: `POST_NOT_EDITABLE(409, "지금은 수정할 수 없는 게시물입니다.")`, `POST_VERSION_CONFLICT(409, "다른 곳에서 게시물이 먼저 바뀌었습니다. 새로 불러온 뒤 다시 저장해 주세요.")`, `POST_CAPTION_TOO_LONG(400, "캡션이 2,200자를 넘었습니다. 줄여서 다시 저장해 주세요.")`.
  - 엔티티 메서드: `Post.replaceCaption(String caption, String hashtags, boolean edited)`, `Post.updateMemo(String)`, `PostPhoto.moveTo(int position)`, `Drink.changeBrewery(Brewery)`(null 허용).
- 저장 흐름 (`PostEditService.update(Long id, PostUpdateRequest req)`, 클래스·메서드에 `@Transactional` 없음, `AnalysisService`처럼 `TransactionTemplate`):
  1. 읽기 트랜잭션: 게시물이 없으면 404, `!editable` → `POST_NOT_EDITABLE`, `version` 불일치 → `POST_VERSION_CONFLICT`. `photos`가 비면 `POST_PHOTO_COUNT`, id 중복·이 게시물에 없는 id → `COMMON_VALIDATION_FAILED`. 별점·메모는 `PostIntakeService.validate`와 같은 규칙. 현재 술 id·양조장 id·`CaptionParts.split(caption)`·`captionEdited`를 스냅샷.
  2. `textEdited = captionEdited || head/tail이 스냅샷과 다름`. `identityChanged = 술이나 양조장 id가 바뀜 || breweryEdit != null`.
  3. 새 캡션 결정: `identityChanged && !textEdited`이면 트랜잭션 밖에서 `captionService.compose(new CaptionInput(label, rating, brewery, memo))` (`label`은 새 술 이름·종류·도수·용량·양조장 이름과 게시물의 `tasteNotes`·`pairing`으로 만든 `LabelResult`, boxes는 빈 목록. `brewery`는 고를 양조장에 `breweryEdit`까지 적용한 값이어야 하므로, 저장하지 않는 `Brewery.of(...)` 객체에 `editManually`로 같은 값을 넣어 넘긴다. 4단계는 같은 `breweryEdit`를 실제 레코드에 적용하므로 캡션 블록과 레코드 값이 같다). `ClaudeUnavailableException`이면 로그 후 블록만 교체로 넘어간다. 그 밖이면 `new CaptionParts(head, tail).join(새 양조장)`, `edited = textEdited`.
  4. 쓰기 트랜잭션: 게시물을 다시 읽어 상태·버전이 1단계와 같은지 확인(다르면 `POST_VERSION_CONFLICT`). 술(`drinkId` 조회 또는 `newDrinkName`으로 `Drink.of(name, Names.normalize(name), brewery)` 저장) → `changeBrewery` → `breweryEdit` 적용 → 별점·메모 → 캡션(`CaptionAssembler.length > 2200`이면 `POST_CAPTION_TOO_LONG`, 해시태그는 tail) → 사진: 빠진 행 삭제 + 커밋 뒤 원본·렌더링 파일 삭제, 남은 행 `moveTo(i)`, `fitMode`가 바뀐 사진만 `photoService.rerender(id, 기존 focus 또는 null, mode)` → `clearFailure()` → `transition(id, 현재상태, PENDING_REVIEW, version, now) == 0`이면 `setRollbackOnly` 후 `POST_VERSION_CONFLICT` → `PostEditedEvent` 발행.
  5. 다시 읽어 `PostDetailResponse`로 돌려준다.
- [ ] **Step 1: 실패하는 테스트 작성** — `PostEditServiceTest`(Mockito, `TransactionTemplate`은 실제 `PlatformTransactionManager` 목으로 즉시 실행): `저장하면_PENDING_REVIEW로_전이하고_이벤트를_낸다`, `저장_직전에_버전이_바뀌면_409이고_아무것도_바뀌지_않는다`(transition이 0 반환 → `POST_VERSION_CONFLICT`, 이벤트 없음, 파일 삭제 없음), `컨테이너가_있는_실패_게시물은_수정할_수_없다`, `PUBLISHED는_수정할_수_없다`, `직접_고친_캡션은_양조장_블록만_바뀐다`(`captionService` 호출 없음, head·tail 보존), `캡션을_안_고치고_양조장을_바꾸면_다시_생성한다`, `재생성이_실패하면_양조장_블록만_바꿔_저장한다`, `사진을_모두_빼면_400이다`, `다른_게시물의_사진_id는_400이다`, `여백_넣기로_바꾼_사진만_다시_렌더링한다`(`rerender(id, focus, PAD)` 1회), `캡션이_2200자를_넘으면_400이다`(2,201자 → 400, 2,200자 → 성공), `drinkId와_newDrinkName을_함께_보내면_400이다`. `PostEditIntegrationTest`: `빠진_사진은_행과_파일이_지워지고_순서가_다시_매겨진다`(사진 3장 중 가운데 삭제·순서 뒤집기 → position 0,1, 지운 사진의 rendered 파일 없음), `롤백되면_파일이_그대로다`. `PostNotificationListenerTest`: `수정_이벤트는_커밋_뒤_새_개요를_보낸다`. `PostControllerTest`: `version이_없으면_400이다`.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*PostEdit*' --tests '*PostNotificationListenerTest' --tests '*PostControllerTest'` → 컴파일 실패.
- [ ] **Step 3: 구현** — 위 흐름 그대로. `(post_id, position)`에는 유니크 제약이 없으므로(`V2__post.sql`은 일반 인덱스) 순서 바꾸기는 한 번에 덮어써도 된다.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS, `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 게시물 수정 저장과 새 개요 알림`

### Task 4: 웹 기반 (PWA·서비스 워커·화면 틀·세션 확인)

**Files:**
- Create (web): `app/manifest.ts`, `app/icon.tsx`, `public/sw.js`, `components/pwa/ServiceWorkerRegister.tsx`, `components/layout/AppNav.tsx`, `lib/shared-photos.ts`, `hooks/use-session.ts`
- Modify (web): `app/layout.tsx`
- Create (api): `auth/controller/SessionController.java`에 `GET` 추가 (Modify)
- Test: `lib/shared-photos.test.ts`, `components/layout/AppNav.test.tsx`, api `auth/AuthIntegrationTest.java`에 케이스 추가

**Interfaces:**
- Produces:
  - API `GET /api/v1/sessions/current` → 204(로그인됨). 세션이 없으면 기존 필터가 401 `AUTH_REQUIRED`. `PUBLIC_PATHS`는 바꾸지 않는다.
  - `useSession()` — `useQuery({ queryKey: ["session"], queryFn: () => apiFetch<void>("/api/v1/sessions/current") })`. 401이면 `QueryProvider`가 로그인으로 보낸다.
  - `app/manifest.ts`: `name "k-drinker"`, `short_name "k-drinker"`, `start_url "/"`, `display "standalone"`, 아이콘 192·512 PNG(`app/icon.tsx`의 `generateImageMetadata`로 두 크기, 실제 URL은 `node_modules/next`의 metadata 문서로 확인), `share_target: { action: "/share-target", method: "POST", enctype: "multipart/form-data", params: { files: [{ name: "photos", accept: ["image/jpeg", "image/png", "image/*"] }] } }`.
  - `public/sw.js`: `fetch`에서 `POST /share-target`만 가로챈다. `formData().getAll("photos")`를 Cache `kd-share`에 `/shared/0`…`/shared/{n-1}`(`Content-Type` 유지)과 `/shared/meta`(`{"count": n}` JSON)로 넣는다(넣기 전에 Cache를 비운다). 그 뒤 `Response.redirect("/share", 303)`. 다른 요청은 가로채지 않는다(오프라인 캐시 만들지 않음). `install`에서 `skipWaiting`, `activate`에서 `clients.claim`.
  - `lib/shared-photos.ts`: `SHARE_CACHE = "kd-share"`, `readSharedPhotos(): Promise<File[]>`(meta가 없거나 Cache API가 없으면 `[]`, 파일 이름 `shared-{i}.{jpg|png}`), `clearSharedPhotos(): Promise<void>`.
  - `ServiceWorkerRegister`(`"use client"`): 마운트 때 `navigator.serviceWorker?.register("/sw.js", { scope: "/" })`. 실패는 무시.
  - `AppNav`: `<Link>` 세 개 `기록`(`/`) `양조장`(`/breweries`) `설정`(`/settings`), 현재 경로에 `aria-current="page"`. 레이아웃에는 넣지 않고 기록·양조장·설정·수정 화면이 직접 넣는다(`/login`·`/share`에는 없음).
- [ ] **Step 1: 실패하는 테스트 작성** — `shared-photos.test.ts`(전역 `caches`를 메모리 가짜로): `returns photos in shared order`, `returns empty when nothing was shared`, `returns empty when Cache API is missing`, `clears shared photos`. `AppNav.test.tsx`: `marks the current page`. api `AuthIntegrationTest`: `세션이_있으면_현재_세션은_204다`, `세션이_없으면_현재_세션은_401이다`.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패, `./gradlew test --tests '*AuthIntegrationTest'` → 실패.
- [ ] **Step 3: 구현** — Interfaces 그대로. `layout.tsx`에 `ServiceWorkerRegister`와 `viewport`/`themeColor`만 더한다.
- [ ] **Step 4: 통과 확인** — `pnpm test && pnpm lint` PASS, `API_INTERNAL_URL=http://localhost:8080 pnpm build` 성공(빌드 출력에 `/manifest.webmanifest` 포함), `./gradlew test` PASS.
- [ ] **Step 5: 커밋** — `Feat(web) : PWA 공유 대상과 서비스 워커`

### Task 5: 공유 입력 화면 `/share`

**Files:**
- Create: `app/share/page.tsx`, `components/share/ShareForm.tsx`, `components/ui/RatingInput.tsx`, `hooks/use-create-post.ts`, `types/api/post.ts`
- Test: `components/share/ShareForm.test.tsx`, `components/ui/RatingInput.test.tsx`

**Interfaces:**
- Consumes: `readSharedPhotos`, `clearSharedPhotos`, `useSession` (Task 4), `POST /api/v1/posts`(multipart `photos`·`memo`·`rating`·`source=SHARE`, 기존).
- Produces:
  - `types/api/post.ts`: `PostCreateResponse { id: number; status: string }`과 Task 1의 `PostSummaryResponse`·`PostDetailResponse`·`PostPhotoResponse`, Task 3의 `PostUpdateRequest`·`PhotoEdit`, `PageResponse<T>`(`types/api/common.ts`에 추가).
  - `RatingInput({ value: number | null, onChange(value: number | null) })` — "별점 없음"과 0.5~5.0(0.5 단위) 선택. `<select>` 또는 버튼 묶음, 접근 가능한 이름 "별점".
  - `useCreatePost()` — `useMutation`으로 `FormData`를 `apiFetch<PostCreateResponse>("/api/v1/posts", { method: "POST", body })`.
- 화면 동작: 먼저 `useSession()`(로그인 안 됐으면 `QueryProvider`가 `/login?next=%2Fshare`로 보냄, 사진은 Cache에 그대로). 사진 미리보기(`URL.createObjectURL`, 언마운트 때 revoke), 별점, 한 줄 메모(`<input>`, 2000자 제한), [보내기]. 보내기 성공 → `clearSharedPhotos()` 후 "접수했습니다. 분석이 끝나면 텔레그램으로 개요를 보내 드립니다." 표시. 사진이 0장이면 "공유된 사진이 없습니다. 갤러리에서 사진을 골라 k-drinker로 공유해 주세요." 11장 이상이면 [보내기]를 막고 "사진은 1장 이상 10장 이하로 올려 주세요."(API `POST_PHOTO_COUNT`와 같은 문장). API 오류는 `ApiError.message`를 `role="alert"`로.
- [ ] **Step 1: 실패하는 테스트 작성** — `ShareForm.test.tsx`(`readSharedPhotos`·`apiFetch` 목): `shows shared photo previews`, `sends photos memo rating and SHARE source`(FormData의 `photos` 2개, `source`="SHARE", `rating`="4.5"), `omits rating when none is chosen`, `clears shared photos after sending`, `keeps shared photos until sent`(전송 실패 시 `clearSharedPhotos` 미호출), `blocks sending more than ten photos`, `shows guidance when nothing was shared`. `RatingInput.test.tsx`: `offers no rating and half steps up to five`(선택지 11개: "별점 없음" + 0.5, 1.0 … 5.0), `reports null when no rating is chosen`.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 위 동작. `app/share/page.tsx`는 서버 컴포넌트로 두고 `ShareForm`(클라이언트)을 `Suspense`로 감싼다.
- [ ] **Step 4: 통과 확인** — `pnpm test && pnpm lint` PASS.
- [ ] **Step 5: 커밋** — `Feat(web) : 갤러리 공유 입력 화면`

### Task 6: 게시물 수정 화면 `/posts/[id]/edit`

**Files:**
- Create: `app/posts/[id]/edit/page.tsx`, `components/post-edit/PostEditForm.tsx`, `components/post-edit/DrinkPicker.tsx`, `components/post-edit/BreweryPicker.tsx`, `components/post-edit/PhotoOrderList.tsx`, `hooks/use-post.ts`, `hooks/use-update-post.ts`, `hooks/use-drink-search.ts`, `hooks/use-brewery-search.ts`, `types/api/brewery.ts`, `types/api/drink.ts`
- Test: `components/post-edit/PostEditForm.test.tsx`, `components/post-edit/PhotoOrderList.test.tsx`

**Interfaces:**
- Consumes: `GET/PUT /api/v1/posts/{id}` (Task 1, 3), `GET /api/v1/drinks?q=`, `GET /api/v1/breweries?q=` (Task 2), `RatingInput` (Task 5).
- Produces: `usePost(id)`(키 `["posts", id]`), `useUpdatePost(id)`(성공 시 `["posts"]` invalidate), `useDrinkSearch(q)`·`useBrewerySearch(q)`(q가 비면 `enabled: false`), `BreweryResponse`·`BreweryRequest`·`DrinkResponse` 타입.
- 화면: `editable`이 false면 상태와 "지금은 수정할 수 없는 게시물입니다."만 보여 주고 폼을 내지 않는다. 폼 칸: 술(검색해서 고르기 또는 "새 이름으로" 입력), 양조장(검색해 고르기, 고른 양조장의 주소·전화·홈페이지·인스타그램·기타 SNS 칸 수정 — 고친 칸이 있을 때만 `breweryEdit`를 보낸다, "고치면 다른 게시물에도 반영됩니다" 안내), 별점, 메모, 캡션 머리(textarea), 양조장 블록(읽기 전용 `<pre>`), 캡션 꼬리(해시태그 textarea), 사진 목록(각 사진에 [위로] [아래로] [삭제] 버튼과 "여백 넣기" 체크박스, 마지막 한 장은 [삭제] 비활성), 글자 수는 API가 준 `captionLength`를 "캡션 N자 / 2,200자"로 표시(저장 뒤 갱신, 입력 중 계산하지 않음). [저장] 성공 → "저장했습니다. 텔레그램으로 새 개요를 보내 드립니다." 409·400은 `ApiError.message`를 `role="alert"`로. 사진 `<img>`는 `url?v={version}`으로 캐시를 깬다.
- [ ] **Step 1: 실패하는 테스트 작성** — `PostEditForm.test.tsx`(`apiFetch` 목): `fills the form from the post`, `sends version and photo order on save`(PUT body의 `version`, `photos` 순서, `captionHead`/`captionTail`), `sends brewery edit only when a field changed`, `shows the conflict message`(ApiError `POST_VERSION_CONFLICT`), `shows a read-only notice when not editable`, `sends newDrinkName instead of drinkId for a new drink`. `PhotoOrderList.test.tsx`: `moves a photo up`, `cannot delete the last photo`, `toggles padding`.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 위 화면. 라우트 파라미터는 Next 16 규칙(`params`가 Promise)대로, `cacheComponents`에서 필요한 `Suspense` 경계를 둔다. 실제 시그니처는 `node_modules/next/dist/docs` 또는 타입으로 확인.
- [ ] **Step 4: 통과 확인** — `pnpm test && pnpm lint` PASS, `API_INTERNAL_URL=http://localhost:8080 pnpm build` 성공.
- [ ] **Step 5: 커밋** — `Feat(web) : 게시물 수정 화면`

### Task 7: 기록 목록·양조장 관리·설정 화면

**Files:**
- Modify: `app/page.tsx`(헬스 표시 대신 기록 목록), 
- Create: `components/posts/PostList.tsx`, `hooks/use-posts.ts`, `app/breweries/page.tsx`, `app/breweries/[id]/page.tsx`, `app/breweries/new/page.tsx`, `components/breweries/BreweryList.tsx`, `components/breweries/BreweryForm.tsx`, `hooks/use-breweries.ts`, `app/settings/page.tsx`, `components/settings/InstagramTokenPanel.tsx`, `hooks/use-instagram-token.ts`, `types/api/instagram.ts`, `components/ui/Pager.tsx`
- Delete: `components/health/HealthStatus.tsx`·`.test.tsx`·`hooks/use-health.ts`·`types/api/health.ts`는 다른 곳에서 안 쓰면 지운다(`grep`으로 확인).
- Test: `components/posts/PostList.test.tsx`, `components/breweries/BreweryForm.test.tsx`, `components/settings/InstagramTokenPanel.test.tsx`, `components/ui/Pager.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/posts` (Task 1), 양조장 API (Task 2), `GET /api/v1/instagram/token`·`POST /api/v1/instagram/token/reload`(기존, `InstagramTokenResponse { expiresAt: string | null; refreshedAt: string | null }`), `AppNav` (Task 4).
- 화면:
  - 기록(`/`): 썸네일·술 이름(없으면 "이름 미확인")·별점·상태 한국어 라벨(`RECEIVED` 접수됨, `ANALYZING` 분석 중, `PENDING_REVIEW` 검토 대기, `PUBLISHING` 게시 중, `PUBLISHED` 게시됨, `FAILED` 실패, `CANCELED` 취소됨)·날짜(Asia/Seoul `yyyy.MM.dd`). `PENDING_REVIEW`·`FAILED`는 수정 화면 `<Link>`, `PUBLISHED`는 permalink 외부 링크. `Pager`(이전/다음, `?page=` 쿼리).
  - 양조장(`/breweries`): 검색 입력 + 목록(이름·주소·검증 여부) + [새 양조장]. 상세(`/breweries/[id]`)·새로 만들기(`/breweries/new`)는 `BreweryForm`. 저장 성공 메시지 "저장했습니다. 다음 게시물부터 이 정보가 쓰입니다."
  - 설정(`/settings`): 인스타그램 토큰 마지막 반영 시각과 [토큰 다시 불러오기] 버튼. 성공하면 "환경변수의 토큰을 다시 불러왔습니다." 실패는 `ApiError.message`(예: `INSTAGRAM_TOKEN_MISSING`). 토큰 값은 표시하지 않는다(API도 주지 않음).
- [ ] **Step 1: 실패하는 테스트 작성** — `PostList.test.tsx`: `links reviewable posts to the edit screen`, `links published posts to instagram`, `shows status labels in Korean`, `shows a placeholder for an unknown drink`. `BreweryForm.test.tsx`: `requires a name`, `shows the server field error`, `shows the saved message`. `InstagramTokenPanel.test.tsx`: `reloads the token and shows the time`, `shows the missing token message`. `Pager.test.tsx`: `disables previous on the first page`, `disables next on the last page`.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → 실패.
- [ ] **Step 3: 구현** — 위 화면. 날짜 표시는 `Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul" })`.
- [ ] **Step 4: 통과 확인** — `pnpm test && pnpm lint` PASS, `API_INTERNAL_URL=http://localhost:8080 pnpm build` 성공.
- [ ] **Step 5: 커밋** — `Feat(web) : 기록·양조장 관리·설정 화면`

### Task 8: 운영 (사진 정리·Claude 사용량 로그·문서)

**Files:**
- Create: `media/MediaCleanup.java`, `media/MediaCleanupProperties.java`(또는 `@Value`)
- Modify: `post/repository/PostRepository.java`, `media/PhotoStorage.java`, `analysis/claude/ClaudeCalls.java`, `resources/application.yml`, `deploy/README.md`
- Test: `media/MediaCleanupTest.java`, `media/PhotoStorageTest.java`, `analysis/claude/ClaudeCallsTest.java`

**Interfaces:**
- Consumes: `PhotoStorage`, `PostPhotoRepository`, `Clock`.
- Produces:
  - `application.yml`: `app.media.cleanup.cron: "0 0 4 * * *"`, `zone: Asia/Seoul`, `canceled-retention: 7d`, `staging-retention: 1d`.
  - `PostRepository.findIdsByStatusUpdatedBefore(PostStatus status, Instant before)`.
  - `MediaCleanup.run()`(`@Scheduled(cron = "${app.media.cleanup.cron}", zone = "${app.media.cleanup.zone}")`): (1) `CANCELED`이고 `updatedAt < now - 7d`인 게시물의 `PostPhoto` 행을 짧은 트랜잭션으로 지우고, 커밋 뒤 원본·렌더링 파일과 `original/{postId}/` 디렉터리를 지운다. 게시물 행은 기록으로 남긴다. 행이 이미 없으면 건너뛴다(멱등). (2) `PhotoStorage.deleteStagingOlderThan(Instant)`로 `tmp/` 아래 수정 시각이 1일 지난 스테이징 디렉터리를 지운다. 끝나면 `log.info("사진 정리: 취소 게시물 {}건, 사진 {}장, 임시 폴더 {}개", …)`. 예외는 게시물 단위로 잡아 로그만 남긴다.
  - `PhotoStorage.deleteStagingOlderThan(Instant cutoff): int`, `deletePostOriginalDirQuietly(Long postId)`.
  - `ClaudeCalls`: 응답마다 `log.info("Claude 사용량 model={} input={} output={} cacheRead={} cacheWrite={} webSearch={}", …)`. 사용량 필드 이름은 Gradle 캐시의 `com.anthropic` 소스(`BetaUsage`)로 확인하고, 없는 값은 0으로 찍는다. `structured`·`create`의 시그니처는 바꾸지 않는다(단계 이름 없이 모델과 토큰만 남긴다).
  - 백업은 이미 DB와 사진(`original/`·`rendered/`)을 모두 남긴다(`deploy/backup.sh`, T2). 이 태스크에서 바꾸지 않는다.
  - `deploy/README.md`: (a) 4장에 "사진 정리" 절(매일 04:00, 취소 7일·임시 1일, 로그 확인 `docker compose ... logs api | grep '사진 정리'`), (b) "Claude 사용량" 절(`grep 'Claude 사용량'`과 하루 합계 내는 `awk` 한 줄), (c) "휴대폰에 앱 설치·공유" 절(안드로이드 크롬에서 도메인 열기 → 로그인 → 메뉴 [홈 화면에 추가] → 갤러리 공유 목록에 k-drinker가 보이는지), (d) 10장 409행의 "웹 설정 화면의 [토큰 다시 불러오기] 버튼은 P4 이후에 생기므로…" 문장을 "웹 설정 화면(`/settings`)에서 [토큰 다시 불러오기]를 누른다"로 바꾼다.
- [ ] **Step 1: 실패하는 테스트 작성** — `MediaCleanupTest`(`@SpringBootTest` 또는 `@DataJpaTest` + 임시 디렉터리, `Clock` 고정): `7일_지난_취소_게시물의_사진을_지운다`, `7일이_안_된_취소_게시물은_그대로다`(경계: 정확히 7일 전은 지우지 않음, 7일 1초 전은 지움), `게시된_게시물의_사진은_지우지_않는다`, `두_번_돌려도_오류가_없다`. `PhotoStorageTest`: `하루_지난_임시_폴더만_지운다`. `ClaudeCallsTest`: `응답의_토큰_사용량을_로그로_남긴다`(로그 캡처, 예: Logback `ListAppender`).
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*MediaCleanupTest' --tests '*PhotoStorageTest' --tests '*ClaudeCallsTest'` → 실패.
- [ ] **Step 3: 구현** — 위 Interfaces 그대로.
- [ ] **Step 4: 통과 확인** — 같은 명령 → PASS, `./gradlew test` 전체 PASS.
- [ ] **Step 5: 커밋** — `Feat(api) : 취소 사진 정리와 Claude 사용량 로그` (README 변경 포함)

### Task 9: 실제 기기 검증과 문서 정리

**Files:**
- Modify: 이 계획 문서 끝에 검증 결과 표, `docs/superpowers/specs/2026-11-14-review-to-instagram-design.md`의 T8·T9 "(완료)" 표시

- [ ] **Step 1: 로컬 구성 확인** — `deploy/README.md` 8장대로 로컬 compose를 띄우고 브라우저에서 `/`, `/breweries`, `/settings`, `/posts/{id}/edit`가 열리는지, `/manifest.webmanifest`에 `share_target`이 있는지 본다.
- [ ] **Step 2: 배포 뒤 실제 기기 검증 (사용자와 함께)** — 사용자가 `dev`→`master` 병합·배포를 요청한 뒤에 한다. 표에 결과를 적는다.

| 항목 | 기대 |
|---|---|
| 홈 화면에 추가 | 갤러리 공유 목록에 k-drinker가 보인다 |
| 사진 3장 공유 → 보내기 | 텔레그램 접수 알림 → 개요 수신 |
| 로그인 풀린 상태에서 공유 | 로그인 화면 → 로그인 → 같은 사진으로 `/share` |
| 11장 공유 | 보내기 막힘 + 안내 (P3에서 보류한 메시지 12 대신 웹 안내) |
| 개요 [수정] → 술·별점·사진 순서 고쳐 저장 | 새 개요 수신, 이전 개요 버튼 제거 |
| 캡션 직접 고친 뒤 양조장 바꿔 저장 | 고친 문장 유지, 양조장 줄만 바뀜 |
| 수정 화면을 열어 둔 채 텔레그램 [확인] → 저장 | 409 문구 |
| 설정 [토큰 다시 불러오기] | 반영 시각 갱신 |

- [ ] **Step 3: 커밋** — `Docs : P4 웹·운영 검증 결과 기록`
