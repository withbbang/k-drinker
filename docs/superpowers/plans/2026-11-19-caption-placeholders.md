# 캡션 이름표와 미리보기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 캡션을 이름표가 든 틀로 저장하고 보여 줄 때·게시할 때 서버가 현재 값으로 채워, 술·양조장·별점을 바꿔도 Claude를 다시 부르지 않게 한다. 수정 화면은 서버 미리보기로 실제 올라갈 글과 글자 수를 본다.

**Architecture:** `caption` 패키지에 `CaptionValues`(채울 값)와 `Placeholders`(채우기·조사·빈 값 처리)를 더하고, 캡션을 글자로 내보내는 모든 곳(게시, 상세 응답, 텔레그램, 글자 수 검사)이 `CaptionAssembler.render(stored, values)`를 거친다. 새 캡션은 생성 단계부터 술 정보·별점·양조장 블록·이름/양조장 해시태그를 이름표로 쓴다. 이름표 없는 기존 캡션은 채우기가 아무것도 바꾸지 않으므로 그대로 동작한다. `PostEditService`의 Claude 재작성 경로는 캡션이 없는 게시물에만 남긴다.

**Tech Stack:** Spring Boot / JUnit (apps/api), Next.js / React Query / Vitest (apps/web)

**Spec:** `docs/superpowers/specs/2026-11-19-web-redesign-design.md` (2단계)

## Global Constraints

- 선행: `docs/superpowers/plans/2026-11-19-brewery-sheet-handoff.md`가 `dev`에 병합된 뒤 `dev`에서 `feat/caption-placeholders`를 딴다.
- 커밋 규칙·lint/test 규칙은 선행 계획의 Global Constraints와 같다.
- 이름표(정확히 이 13개): `{술이름}` `{술이름태그}` `{종류}` `{도수}` `{용량}` `{별점}` `{양조장}` `{양조장태그}` `{주소}` `{전화}` `{홈페이지}` `{인스타그램}` `{기타SNS}`.
- 값 형식: `{도수}` = `ClaudeCaptionWriter.abv` 규칙 + `%` (예: `6%`, `16.5%`), `{용량}` = `750ml`, `{별점}` = `CaptionAssembler.ratingBlock` (예: `★4.0`), `{홈페이지}` = `https://`와 끝 `/`를 뗀 글자, `{인스타그램}` = `@핸들`, `{술이름태그}`·`{양조장태그}` = `Hashtags.normalize` 규칙에서 `#`을 뗀 글자. 이 규칙들은 기존 코드에 이미 있으니 옮겨 쓰지 말고 재사용한다.
- 조사 표기: 이름표 바로 뒤에 `은(는)` `이(가)` `을(를)` `과(와)` `으로(로)` `이에요(예요)` 중 하나를 붙인다. 채울 때 값의 마지막 글자 받침으로 고른다. 받침 있음 → 괄호 밖, 없음 → 괄호 안. `으로(로)`는 받침이 없거나 ㄹ이면 `로`. 한글이 아닌 마지막 글자: 숫자 `0 1 3 6 7 8`은 받침 있음, 그 밖의 숫자·로마자·기호는 받침 없음.
- 도메인 로직(채우기·조사·글자 수)은 `apps/api`에만. 웹은 미리보기 API 결과를 표시만 한다.
- 캡션 문구 규칙을 바꾸면 `docs/instagram-caption.md`를 같은 커밋에서 고친다.
- API 응답·에러·URL은 `api-conventions` 스킬을 먼저 읽고 따른다.

## Review Focus

- 해시태그 줄의 이름표가 비는 경우(양조장 없음) → 줄 전체가 아니라 그 태그 하나만 빠진다. Task 1에 테스트.
- 채운 뒤 같은 해시태그가 두 번 나오는 경우(술 이름이 고정 태그와 같음, 예: 막걸리) → 뒤의 것을 뺀다. Task 1에 테스트.
- 사용자가 글에 `{메뉴}`처럼 목록에 없는 중괄호를 쓴 경우 → 글자 그대로 남는다. Task 1에 테스트.
- 이름표 없는 기존 캡션의 게시·글자 수·텔레그램 [상세] → 이번 변경 전과 똑같은 결과. Task 2에 테스트.
- 새 술 이름으로 바꿨을 때(술 레코드에 종류·도수 없음) 미리보기 → 그 이름표가 든 줄만 빠지고 나머지는 남는다. Task 5에 테스트.

---

### Task 1: 채울 값과 채우기

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/caption/CaptionValues.java`
- Create: `apps/api/src/main/java/com/kdrinker/caption/Placeholders.java`
- Create: `apps/api/src/main/java/com/kdrinker/caption/Josa.java`
- Test: `apps/api/src/test/java/com/kdrinker/caption/PlaceholdersTest.java`, `JosaTest.java`, `CaptionValuesTest.java`

**Interfaces:**
- Produces: `record CaptionValues(String drinkName, String category, BigDecimal abv, Integer volumeMl, BigDecimal rating, Brewery brewery)` with `static CaptionValues of(Drink drinkOrNull, BigDecimal rating)` (양조장은 `drink.getBrewery()`), `static CaptionValues of(String drinkName, Drink drinkOrNull, Brewery breweryOrNull, BigDecimal rating)` (미리보기·새 술 이름용), `static final CaptionValues EMPTY`, `Map<String, String> asMap()` (이름표 → 값, 값 없으면 키 없음).
- Produces: `Placeholders.NAMES: List<String>` (위 13개, 중괄호 없이), `static boolean isTemplate(String stored)` (목록의 이름표가 하나라도 있으면 true), `static String fill(String stored, CaptionValues v)`.
- Produces: `Josa.pick(String word, String pair): String` — `pair`는 `"은(는)"` 같은 표기.

`fill` 규칙 (순서대로):
1. 줄 단위로 처리한다. 마커 줄은 그대로 둔다.
2. `#`으로 시작하는 낱말에 든 이름표가 값이 없으면 그 낱말만 뺀다.
3. 그 밖의 줄에 값이 없는 이름표가 하나라도 있으면 그 줄을 뺀다.
4. 남은 이름표를 값으로 바꾸고, 바로 뒤의 조사 표기를 `Josa.pick`으로 고른다.
5. 해시태그 줄에서 같은 태그가 다시 나오면 뒤의 것을 뺀다.
6. 빈 줄이 두 번 넘게 이어지면 하나로 접는다 (`stripMarkers`와 같은 규칙).

- [ ] **Step 1: 실패하는 테스트**
  - `JosaTest`: `("지평 막걸리","은(는)")→"는"`, `("청","은(는)")→"은"`, `("서울장수","이(가)")→"가"`, `("화요","을(를)")→"를"`, `("술","으로(로)")→"로"`, `("밥","으로(로)")→"으로"`, `("탁주","이에요(예요)")→"예요"`, `("약주7","이(가)")→"이"`, `("ABC","은(는)")→"는"`.
  - `PlaceholdersTest.fill_replacesWithParticle`: `"{술이름}은(는) {양조장}에서 만든 {종류}예요."` + 지평 막걸리/지평주조/탁주 → `"지평 막걸리는 지평주조에서 만든 탁주예요."`
  - `fill_dropsLineWithEmptyPlaceholder`: `"첫 줄\n{도수}, {용량}\n끝"` + 도수 없음 → `"첫 줄\n끝"`.
  - `fill_dropsOnlyEmptyHashtag`: `"#전통주 #{술이름태그} #{양조장태그}"` + 양조장 없음 → `"#전통주 #지평막걸리"`.
  - `fill_dedupesHashtags`: 술 이름 "막걸리" → `"#막걸리 #{술이름태그}"` → `"#막걸리"`.
  - `fill_keepsUnknownBraces`: `"{메뉴}는 파전"` 그대로.
  - `fill_breweryBlockLines`: 마커 사이 `🍶 {양조장}\n📍 {주소}\n📷 {인스타그램}\n{기타SNS}` + 주소만 없음 → 주소 줄만 빠지고 `📷 @jipyeong`.
  - `fill_legacyCaptionUnchanged`: 이름표 없는 캡션은 입력과 같다.
  - `isTemplate`: `"{술이름}"` true, `"{메뉴}"` false, null false.
  - `CaptionValuesTest.asMap`: 도수 6.50 → `"6.5%"`, 홈페이지 `https://a.com/` → `"a.com"`, 핸들 `@@x` → `"@x"`, 별점 4 → `"★4.0"`.
- [ ] **Step 2: 실행해 실패 확인** — `./gradlew test --tests '*caption*'` → FAIL
- [ ] **Step 3: 구현** — Interfaces대로. 값 형식은 `ClaudeCaptionWriter.abv`, `BreweryBlock`의 `homepage`/`handle`, `Hashtags.normalize`, `CaptionAssembler.ratingBlock`을 package-private 이상으로 열어 재사용한다.
- [ ] **Step 4: 실행해 통과 확인** → PASS
- [ ] **Step 5: 커밋** — `Feat(api) : 캡션 이름표 채우기와 조사 처리`

### Task 2: 캡션을 내보내는 모든 곳에서 채우기

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/caption/CaptionAssembler.java`
- Modify: `apps/api/src/main/java/com/kdrinker/publish/PublishWorker.java:157`, `publish/PublishService.java:110`
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostQueryService.java` (상세·요약 응답)
- Modify: `apps/api/src/main/java/com/kdrinker/post/dto/PostDetailResponse.java` (`captionTemplated` 추가)
- Modify: `apps/api/src/main/java/com/kdrinker/telegram/MessageFormatter.java:41,57-60,161`
- Test: `CaptionAssemblerTest`, `PublishWorkerTest`, `PublishServiceTest`, `PostQueryServiceTest`, `MessageFormatterTest`

**Interfaces:**
- Consumes: `Placeholders.fill`, `CaptionValues.of(Drink, BigDecimal)` (Task 1).
- Produces: `CaptionAssembler.render(String stored, CaptionValues v): String` = `stripMarkers(fill(stored, v))`, `CaptionAssembler.length(String stored, CaptionValues v): int`. 한 인자짜리 `length(String)`은 지워서 모든 호출이 값을 넘기게 한다.
- Produces: `PostDetailResponse.captionTemplated: boolean` (`Placeholders.isTemplate(post.getCaption())`). `breweryBlock` 필드는 저장된 마커 사이를 채운 결과(이름표 없으면 지금처럼 `BreweryBlock.render`).

- [ ] **Step 1: 실패하는 테스트** — 각 호출 지점에서 이름표 캡션이 채워진 글로 나가는지: `PublishWorkerTest`(인스타그램에 보내는 캡션에 `{`가 없고 술 이름이 들어감), `PublishServiceTest`(2,200자 검사가 채운 길이 기준), `PostQueryServiceTest`(`publishCaption`·`captionLength`·`breweryBlock`이 채운 값, `captionTemplated`), `MessageFormatterTest`(개요의 캡션 글자 수와 [상세] 원문이 채운 글). 각 테스트에 이름표 없는 기존 캡션의 결과가 그대로인 경우를 하나씩 둔다.
- [ ] **Step 2: 실행해 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 호출 지점마다 `CaptionValues.of(post.getDrink(), post.getRating())`을 만든다. `MessageFormatter.detailCaption`·`overview`는 이미 `Post`를 받으니 안에서 만든다.
- [ ] **Step 4: 실행해 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 5: 커밋** — `Feat(api) : 게시·표시 캡션에 이름표 채우기`

### Task 3: 새 캡션을 이름표로 만들기

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/caption/CaptionAssembler.java` (`assemble`)
- Modify: `apps/api/src/main/java/com/kdrinker/caption/BreweryBlock.java` (`TEMPLATE` 추가)
- Modify: `apps/api/src/main/java/com/kdrinker/caption/Hashtags.java`, `HashtagSet.java`
- Modify: `apps/api/src/main/java/com/kdrinker/caption/CaptionService.java`
- Modify: `apps/api/src/main/java/com/kdrinker/caption/ClaudeCaptionWriter.java` (`SYSTEM`)
- Modify: `docs/instagram-caption.md`
- Test: `CaptionAssemblerTest`, `BreweryBlockTest`, `HashtagsTest`, `CaptionServiceTest`, `ClaudeCaptionWriterTest`

**Interfaces:**
- Consumes: `CaptionAssembler.length(stored, values)` (Task 2), `CaptionValues` (Task 1).
- Produces: `BreweryBlock.TEMPLATE: String` = 마커, `🍶 {양조장}`, `📍 {주소}`, `☎ {전화}`, `🏠 {홈페이지}`, `📷 {인스타그램}`, `{기타SNS}`, 마커 (줄바꿈으로 잇는다). 기타 SNS 줄에는 이모지를 붙이지 않는다 (이모지 목록을 늘리지 않기 위해).
- Produces: `assemble(CaptionDraft d, String hashtags)` — 별점은 항상 `{별점}` 줄, 양조장은 항상 `BreweryBlock.TEMPLATE`.
- Produces: `Hashtags.finalize`의 이름 태그는 술 이름이 있으면 `#{술이름태그}`, 양조장 태그는 양조장이 있으면 `#{양조장태그}` (Claude의 NAME·BREWERY 태그는 쓰지 않는다).
- `CaptionService.compose`의 2,200자 검사와 줄이기는 `CaptionValues.of(in.label().drinkName(), null, in.brewery(), in.rating())`에 라벨의 종류·도수·용량을 담은 값으로 채운 길이를 기준으로 한다 (`CaptionValues`에 `static CaptionValues of(LabelResult, Brewery, BigDecimal)`를 더한다).
- Claude가 쓴 hook·intro·tasting에서 목록에 없는 `{…}`는 지운다 (`Placeholders.stripUnknown(String)`). 사용자가 쓴 글에는 쓰지 않는다.

- [ ] **Step 1: 실패하는 테스트**
  - `CaptionAssemblerTest.assemble_usesPlaceholders`: 결과에 `{별점}` 줄과 `BreweryBlock.TEMPLATE`가 있고, 별점이 null이어도 `{별점}` 줄이 있다.
  - `HashtagsTest`: 술 이름이 있으면 `#{술이름태그}`, 양조장이 없으면 양조장 태그 없음.
  - `CaptionServiceTest.compose_lengthCheckUsesFilledLength`: 이름표 글자가 아니라 채운 글 기준으로 2,200자를 판단.
  - `CaptionServiceTest.compose_stripsUnknownPlaceholdersFromClaude`: Claude 초안의 `{메뉴}`가 결과에 없다.
  - `ClaudeCaptionWriterTest.systemPromptMentionsPlaceholders`: `SYSTEM`에 `{술이름}`, `{종류}`, `{도수}`, `{용량}`, `{양조장}`, `은(는)` 표기 설명이 있다.
- [ ] **Step 2: 실행해 실패 확인** → FAIL
- [ ] **Step 3: 구현** — `SYSTEM`의 hook·intro 규칙에 "술 이름·종류·도수·용량·양조장 이름은 직접 쓰지 말고 이름표로 쓴다. 값이 '없음'인 항목의 이름표는 쓰지 않는다. 이름표 바로 뒤 조사는 `{술이름}은(는)`처럼 두 형태를 괄호로 함께 쓴다"를 넣는다. NAME·BREWERY 태그 설명은 빼고 "이름·양조장 태그는 코드가 붙인다"로 바꾼다.
- [ ] **Step 4: `docs/instagram-caption.md` 갱신** — 캡션 틀(별점·양조장 블록을 이름표로), 양조장 블록 템플릿과 예시, 해시태그 구성(이름·양조장 태그는 코드가 이름표로), 새 절 "이름표"(13개 목록·값 형식·조사 표기·빈 값 규칙), Claude 프롬프트 요지. "수정 화면에서 별점을 바꾸고…별점 블록을 새 값으로 바꾼다" 문단은 "이름표 캡션은 별점이 `{별점}`으로 자동 반영, 이름표 없는 기존 캡션은 지금 규칙"으로 고친다.
- [ ] **Step 5: 실행해 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 6: 커밋** — `Feat(api) : 새 캡션을 이름표 틀로 생성`

### Task 4: 수정 저장에서 재작성 제거

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostEditService.java` (`snapshot`, `decideCaption`)
- Modify: `apps/api/src/main/java/com/kdrinker/caption/CaptionParts.java` (`joinTemplate()`)
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/BreweryCaptionSync.java` (이름표 캡션은 블록을 다시 그리지 않음, 버전·담당 처리는 그대로)
- Test: `PostEditServiceTest`, `PostEditIntegrationTest`, `CaptionPartsTest`, `BreweryCaptionSyncTest`

**Interfaces:**
- Produces: `CaptionParts.joinTemplate(): String` — 머리, `BreweryBlock.TEMPLATE`, 꼬리를 빈 줄로 잇는다.
- `decideCaption`: Claude 호출은 `captionMissing && !editedNow`일 때만. 그 밖에는 저장된 캡션이 이름표 캡션이면 `parts.joinTemplate()`, 아니면 지금처럼 `parts.withRating(...)` → `join(captionBrewery)` (기존 캡션의 동작 유지).

- [ ] **Step 1: 실패하는 테스트**
  - `PostEditServiceTest.drinkChange_doesNotCallClaude`: 이름표 캡션 + 술 변경 + 문장 그대로 → `CaptionService.compose` 호출 0회, 저장된 캡션은 틀 그대로, 상세 응답의 채운 글에 새 술 이름.
  - `drinkChange_legacyCaption_keepsText`: 이름표 없는 캡션 + 술 변경 → Claude 0회, 문장 그대로, 양조장 블록만 새 양조장.
  - `missingCaption_stillGenerates`: 캡션 없는 실패 게시물 저장 → compose 1회 (기존 동작).
  - `BreweryCaptionSyncTest.templateCaption_notRewritten`: 이름표 캡션은 캡션 문자열이 그대로이고 버전은 오른다.
- [ ] **Step 2: 실행해 실패 확인** → FAIL
- [ ] **Step 3: 구현** — 재작성 경로(`generate`의 `identityChanged && !textEdited` 조건)를 지우고, 쓰지 않게 된 `identityChanged` 계산과 `Snapshot` 필드를 정리한다. Claude 실패 시 폴백 로그도 캡션 없는 경우에만 남는다.
- [ ] **Step 4: 실행해 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 5: 커밋** — `Refactor(api) : 수정 저장 시 캡션 재작성 제거`

### Task 5: 캡션 미리보기 API

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/post/dto/CaptionPreviewRequest.java`, `CaptionPreviewResponse.java`
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostEditService.java` (`preview`, 대상 술·양조장 결정 로직을 `snapshot`과 공유하는 private 메서드로 뽑기)
- Modify: `apps/api/src/main/java/com/kdrinker/post/controller/PostController.java`
- Test: `PostEditServiceTest`, `PostControllerTest`

**Interfaces:**
- Produces: `POST /api/v1/posts/{id}/caption-preview` (URL은 `api-conventions` 확인 후 확정) — 요청 `CaptionPreviewRequest(Long drinkId, String newDrinkName, Long breweryId, BigDecimal rating, String captionHead, String captionTail)`, 응답 `ApiResponse<CaptionPreviewResponse(String caption, int length, boolean templated)>`. 저장하지 않고 Claude를 부르지 않는다.
- Produces: `PostEditService.preview(Long id, CaptionPreviewRequest req): CaptionPreviewResponse` — 저장과 같은 규칙으로 대상 술·양조장을 정하고, 머리·꼬리를 저장된 캡션의 형식(이름표 캡션이면 `joinTemplate`, 아니면 `join(양조장)`)으로 이어 `CaptionAssembler.render`·`length`.
- 별점·메모 검증은 저장과 같은 `PostIntakeService.validateMemoAndRating` 규칙(메모 제외). 없는 게시물은 404.

- [ ] **Step 1: 실패하는 테스트**
  - `PostEditServiceTest.preview_fillsWithChosenDrink`: 다른 술 id → 채운 글에 그 술 이름과 그 술의 양조장.
  - `preview_newDrinkName_dropsLinesWithoutValues`: 새 술 이름 → `{술이름}`은 새 이름, `{도수}` 줄은 빠짐, 양조장은 현재 게시물 양조장.
  - `preview_doesNotPersist`: 호출 뒤 게시물 버전·캡션 그대로.
  - `PostControllerTest.captionPreview_returnsWrappedResponse`: 200, `data.caption`, `data.length`, `data.templated`.
- [ ] **Step 2: 실행해 실패 확인** → FAIL
- [ ] **Step 3: 구현** — Interfaces대로.
- [ ] **Step 4: 실행해 통과 확인** — `./gradlew test` → PASS
- [ ] **Step 5: 커밋** — `Feat(api) : 캡션 미리보기 API`

### Task 6: 수정 화면 미리보기

**Files:**
- Create: `apps/web/hooks/use-caption-preview.ts`
- Modify: `apps/web/types/api/post.ts` (`captionTemplated`, `CaptionPreviewRequest`, `CaptionPreviewResponse`)
- Modify: `apps/web/components/post-edit/PostEditForm.tsx`
- Test: `apps/web/hooks/use-caption-preview.test.ts`, `PostEditForm.test.tsx`

**Interfaces:**
- Consumes: 미리보기 API (Task 5).
- Produces: `useCaptionPreview(id: string, input: CaptionPreviewRequest): { data?: CaptionPreviewResponse; isFetching: boolean }` — 입력이 400ms 멈춘 뒤 요청, React Query 키 `["posts", id, "caption-preview", debouncedInput]`, 이전 데이터 유지(`placeholderData: keepPreviousData`).
- `PostEditForm`: 기존 `<pre>{post.breweryBlock}</pre>`와 고정 글자 수 대신 "미리보기" 영역(`whitespace-pre-wrap`, 새 데이터를 기다리는 동안 흐리게)과 `{length}자 / 2,200자`. 2,200자를 넘으면 글자 수를 `role="alert"`로 표시. `!post.captionTemplated`이고 술이 바뀌었으면 `이 글은 이름표가 없어 술 이름이 자동으로 바뀌지 않아요`를 보여 준다. 캡션 머리·꼬리 입력칸은 이번 단계에서 그대로 둔다(한 칸 합치기·이름표 넣기 버튼은 3단계).

- [ ] **Step 1: 실패하는 테스트**
  - `use-caption-preview.test.ts`: 빠르게 세 번 바꾸면 요청은 마지막 값으로 1번.
  - `PostEditForm.test.tsx`: 미리보기 응답의 글과 글자 수가 보인다 / 2,300자 응답이면 경고 / 이름표 없는 게시물에서 술을 바꾸면 안내 문구 / 이름표 게시물에서는 안내 없음.
- [ ] **Step 2: 실행해 실패 확인** — `pnpm test` → FAIL
- [ ] **Step 3: 구현** — Interfaces대로. API 호출은 `lib/api.ts` 래퍼.
- [ ] **Step 4: 실행해 통과 확인** — `pnpm lint && pnpm test` → 통과
- [ ] **Step 5: 커밋** — `Feat(web) : 수정 화면 캡션 미리보기`
