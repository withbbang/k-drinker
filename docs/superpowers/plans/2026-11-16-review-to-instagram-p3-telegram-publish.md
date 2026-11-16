# 전통주 후기 → 인스타그램 P3 텔레그램·게시 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 텔레그램 봇으로 사진·메모를 받아 게시물을 접수하고, 분석 결과를 개요와 [확인] [상세] [수정] [취소] 버튼으로 보낸다. [확인]을 누르면 인스타그램에 게시하고 완료나 실패를 알린다. 장기 토큰은 스스로 갱신한다.

**Architecture:** 텔레그램은 lottery-picker처럼 `SmartLifecycle` 스레드가 `getUpdates`를 롱 폴링한다. 받은 메시지는 `AlbumCollector`가 3초 동안 묶어 기존 `PostIntakeService.receive`로 넘긴다. 알림은 게시물 이벤트(`PostReceivedEvent`, `PostAnalyzedEvent`, 새로 만드는 `PostPublishFinishedEvent`)를 받아 전용 단일 스레드 실행기에서 보낸다. 중복 키는 `telegram_notification` 테이블에 남긴다. 게시는 `PublishService`가 상태 전이로 연타를 막고, 단일 스레드 게시 실행기에서 `InstagramClient`로 컨테이너 생성 → 상태 확인 → publish를 한다. 컨테이너 id를 먼저 저장해서, 재시도할 때 이미 올라간 게시물을 다시 올리지 않는다.

**Tech Stack:** Spring Boot 4.1.2 / Java 17 / JPA / Flyway, `spring-boot-starter-restclient`(+ `-test`), Telegram Bot API, Instagram API with Instagram Login (`graph.instagram.com`).

**Spec:** `docs/superpowers/specs/2026-11-14-review-to-instagram-design.md` (T6, T7). 메시지·버튼 문구는 `docs/telegram-messages.md` (확정본, 글자 그대로), 마커·2,200자 규칙은 `docs/instagram-caption.md`.

| 단계 | 태스크 | 내용 |
|---|---|---|
| P1 기반 | T1~T3 | 완료 |
| P2 분석 | T4, T5 | 완료 |
| **P3 텔레그램·게시** | **T6, T7** | **봇 입력, 알림·버튼, 인스타그램 게시, 토큰 갱신** |
| P4 웹·운영 | T8, T9 | 공유 입력, 수정 화면(이 단계의 [수정] 링크 대상), 토큰 다시 불러오기 버튼, 취소 사진 7일 정리, 백업 |

## Global Constraints

- 경로 약어: `telegram/…` 같은 자바 경로는 `apps/api/src/main/java/com/kdrinker/`(테스트는 `src/test/java/com/kdrinker/`) 아래, `db/migration/…`과 `application.yml`은 `apps/api/src/main/resources/` 아래다.
- 문구: `docs/telegram-messages.md`의 템플릿·고정 사유·팝업 문구를 한 글자도 바꾸지 않는다. `parse_mode: HTML`. 고정 문구 밖의 값(술 이름, 양조장 이름, 주소, 메모, 캡션, 실패 사유)은 `<` `>` `&`를 이스케이프한다. 이모지는 🍶 📍 🏠 📷 ☎ ⚠ 🔗만, 상태 알림에 느낌표·엠 대시·엔 대시 금지.
- 수신자: `app.telegram.chat-id` 하나. 다른 chat의 메시지·콜백은 응답 없이 무시한다(콜백도 `answerCallbackQuery`를 보내지 않는다). `TELEGRAM_BOT_TOKEN` 또는 `TELEGRAM_CHAT_ID`가 비면 폴링도 전송도 하지 않고, 기동은 정상이다.
- 콜백 데이터 `act:{postId}:{version}`, `act` ∈ `ok` `detail` `edit` `cancel` `retry`.
- 전송 실패: 5초 간격으로 최대 3회 다시 시도한다. 그래도 실패하면 로그만 남기고 게시물 상태는 바꾸지 않는다.
- 중복 키(문서의 `analysisAttempt`·`publishAttempt`는 그 시점의 게시물 `version`으로 구현한다. 전이마다 version이 오르므로 시도마다 다르다): `RECEIVED:{id}`, `ANALYSIS_FAILED:{id}:{version}`, `OVERVIEW:{id}:{version}`, `CANCELED:{id}`, `PUBLISHING:{id}:{version}`, `PUBLISHED:{id}`, `PUBLISH_FAILED:{id}:{version}`, `TOKEN_REFRESH_FAILED:{yyyy-MM-dd}`(Asia/Seoul). 상세·수정 링크·텍스트만·장수 초과는 키 없이 매번 보낸다.
- 앨범 묶기: 같은 chat의 마지막 사진 수신 후 3초. 그 3초 안에 온 텍스트는 메모로 붙인다. 입력은 1~10장, 11장 이상이면 다운로드하지 않고 메시지 12를 보낸다.
- 인스타그램: 캐러셀 최대 10장, 캡션 최대 2,200자(마커 제거 후 코드 포인트, `CaptionAssembler.length`). 게시할 캡션은 `CaptionAssembler.stripMarkers(post.getCaption())`. 이미지 URL은 `PhotoService.publicUrl(photo)`. 엔드포인트 수치·에러 코드는 Task 5 Step 1에서 Meta 공식 문서로 확인하고 이 문서의 값과 다르면 공식 문서를 따른다.
- 상태 변경: `PostRepository.transition(...)`으로만 바꾼다. 텔레그램 콜백에서 오는 전이는 버전 검사 오버로드(`expectedVersion`)를 쓴다. 다른 필드는 transition 전에 고치고 transition 후 다시 읽는다 (`.claude/rules/api.md`).
- Claude·텔레그램·인스타그램 HTTP 호출은 트랜잭션 밖에서 한다. DB 쓰기는 `TransactionTemplate`으로 짧게 나눈다 (`AnalysisService`와 같은 이유).
- 새 환경변수는 만들지 않는다. `TELEGRAM_BOT_TOKEN` `TELEGRAM_CHAT_ID` `INSTAGRAM_ACCESS_TOKEN` `INSTAGRAM_USER_ID`는 P1에서 등록됐다. API 주소·버전·폴링 간격은 `application.yml`의 `app.telegram.*`·`app.instagram.*` 고정값으로 둔다.
- 메모리: 사진은 한 장씩 내려받아 바로 넘긴다. 앨범 하나(최대 10장, 장당 약 300KB)만 메모리에 둔다.
- 테스트: `./gradlew test` (Bash 훅이 막으면 `ctx_execute` shell로 실행하고 `build/test-results/test/*.xml`로 센다). HTTP는 `MockRestServiceServer`로 대체하고 실제 텔레그램·인스타그램 호출은 단위 테스트에 넣지 않는다. lottery-picker `apps/api/src/test/java/com/lotterypicker/notification/TelegramClientTest.java`의 `@RestClientTest` 방식을 따른다.

## Review Focus

1. **[확인] 연타, 또는 두 기기에서 동시에 누름**: 게시는 한 번만 시작된다. 두 번째는 오래된 버튼 팝업이 뜬다 → Task 6 `confirmTwiceStartsOnce`.
2. **media_publish 응답 직전에 타임아웃이 나거나 서버가 재시작됨**: [재시도]를 눌러도 두 번 올라가지 않는다. 저장한 컨테이너가 `PUBLISHED`면 다시 올리지 않고 완료로 처리한다 → Task 6 `retryWithPublishedContainerDoesNotRepublish`, `recoversStuckPublishingOnStartup`.
3. **앨범을 보내고 1초 뒤에 메모를 따로 보냄**: 메모로 붙고 "사진이 없어서" 안내는 오지 않는다. 5초 뒤에 오면 안내가 온다 → Task 4 `textWithinWindowBecomesMemo`, `textAfterWindowIsTextOnly`.
4. **술 이름·메모·캡션에 `<` `&`가 들어감** (예: `탁주 <생>`, `A&B`): 텔레그램이 400을 내지 않고 글자 그대로 보인다 → Task 2 `escapesUserValues`.
5. **다른 사람이 봇을 찾아 사진이나 버튼 콜백을 보냄**: 게시물이 생기지 않고 응답도 없다 → Task 4 `ignoresOtherChat`, Task 7 `ignoresCallbackFromOtherChat`.

---

### Task 1: 텔레그램 클라이언트와 알림 전송기

**Files:**
- Modify: `apps/api/build.gradle` (`spring-boot-starter-restclient`, `testImplementation 'org.springframework.boot:spring-boot-starter-restclient-test'`)
- Modify: `apps/api/src/main/resources/application.yml` (`app.telegram.base-url: https://api.telegram.org`, `poll-timeout-seconds: 25`, `read-timeout: 30s`, `album-window: 3s`, `send-retry-delay: 5s`, `send-max-retries: 3`)
- Create: `apps/api/src/main/resources/db/migration/V6__telegram_notification.sql`
- Create: `telegram/TelegramProperties.java`, `telegram/TelegramClient.java`, `telegram/dto/*.java`, `telegram/TelegramSender.java`, `telegram/entity/TelegramNotification.java`, `telegram/repository/TelegramNotificationRepository.java`, `telegram/TelegramExecutorConfig.java` (모두 `apps/api/src/main/java/com/kdrinker/` 아래)
- Test: `telegram/TelegramClientTest.java`, `telegram/TelegramSenderTest.java`

V6:
```sql
CREATE TABLE telegram_notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    dedup_key   VARCHAR(100) NOT NULL,
    post_id     BIGINT       NULL,
    kind        VARCHAR(40)  NOT NULL,
    message_id  BIGINT       NULL,
    sent_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_telegram_notification_key UNIQUE (dedup_key)
);
CREATE INDEX idx_telegram_notification_post_kind ON telegram_notification (post_id, kind);
```

**Interfaces:**
- Produces:
  - `record TelegramText(String html, List<List<InlineButton>> keyboard)`, `record InlineButton(String text, String callbackData)`. `keyboard`가 비면 버튼 없이 보낸다.
  - `TelegramClient`: `boolean isConfigured()`, `boolean isOwnerChat(long chatId)`, `long sendMessage(TelegramText text)` (message_id 반환), `void removeKeyboard(long messageId)` (`editMessageReplyMarkup`, 빈 `inline_keyboard`), `void answerCallback(String callbackQueryId, String textOrNull)`, `void sendPhotos(List<Path> jpegs)` (1장이면 `sendPhoto`, 2~10장이면 `sendMediaGroup`, multipart 업로드), `List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds)` (`allowed_updates: ["message","callback_query"]`), `byte[] downloadFile(String fileId)` (`getFile` → `{base-url}/file/bot{token}/{file_path}`). 응답 `ok:false`나 4xx·5xx는 `TelegramApiException`.
  - `TelegramUpdate(long updateId, TelegramMessage message, CallbackQuery callbackQuery)`, `TelegramMessage(long messageId, long chatId, String text, String caption, String mediaGroupId, List<PhotoSize> photo, Document document)`, `PhotoSize(String fileId, int width, int height, Long fileSize)`, `Document(String fileId, String mimeType, Long fileSize)`, `CallbackQuery(String id, long fromChatId, Long messageId, String data)`. JSON 필드는 Bot API 이름(`update_id`, `media_group_id`, `callback_query` …)을 `@JsonProperty`로 받는다.
  - `TelegramSender`: `Optional<Long> send(String dedupKeyOrNull, NotificationKind kind, Long postIdOrNull, TelegramText text)`. 키가 있으면 먼저 행을 넣고(유니크 위반이면 보내지 않고 empty), 보낸 뒤 `message_id`를 기록한다. 3회 모두 실패하면 행을 지우고 empty. `List<Long> messageIds(Long postId, NotificationKind kind)`.
  - `enum NotificationKind { RECEIVED, ANALYSIS_FAILED, OVERVIEW, DETAIL, EDIT_LINK, CANCELED, PUBLISHING, PUBLISHED, PUBLISH_FAILED, TEXT_ONLY, TOKEN_REFRESH_FAILED, TOO_MANY_PHOTOS }`
  - 실행기 빈 `TelegramExecutorConfig.EXECUTOR = "telegramExecutor"` (스레드 1개, 큐 100, 이름 `telegram-`). `AnalysisExecutorConfig`와 같은 모양.

- [ ] **Step 1: `TelegramClientTest` 작성** (`@RestClientTest`, `MockRestServiceServer`)
  - `sendMessageUsesHtmlAndKeyboard`: 요청 본문에 `chat_id`, `parse_mode:"HTML"`, `reply_markup.inline_keyboard[0][0].callback_data == "ok:128:2"`, 응답 `result.message_id: 77` → 반환 77.
  - `blankTokenIsNotConfigured`: 토큰이 비면 `isConfigured()==false`이고 `sendMessage`는 요청을 보내지 않고 `TelegramApiException`.
  - `downloadFileResolvesPath`: `getFile`이 `file_path:"photos/a.jpg"`를 주면 `/file/bot{token}/photos/a.jpg`를 호출해 바이트를 그대로 돌려준다.
  - `okFalseThrows`: `{"ok":false,"description":"Bad Request"}` → `TelegramApiException`.
- [ ] **Step 2: `TelegramSenderTest` 작성** (`@DataJpaTest` + 목 `TelegramClient`, 대기 시간은 생성자로 받은 `Duration.ZERO`)
  - `sameKeySentOnce`: 같은 키로 두 번 → 클라이언트 호출 1회, 두 번째는 empty.
  - `retriesThreeTimesThenGivesUp`: 클라이언트가 4번 다 예외 → 호출 4회(처음 1 + 재시도 3), empty, 행 없음(같은 키로 다시 보내면 다시 시도한다).
  - `recordsMessageId`: 성공 후 `messageIds(postId, OVERVIEW)`에 77.
- [ ] **Step 3: 실패 확인** — `./gradlew test --tests '*Telegram*'` → 컴파일 실패.
- [ ] **Step 4: 구현** — 클라이언트는 lottery-picker `notification/TelegramClient.java` 구조를 따른다(`RestClient.Builder`, 읽기 타임아웃 30초 > 롱 폴링 25초). 로그에 토큰이 들어간 URL을 남기지 않는다.
- [ ] **Step 5: 통과 확인** — `./gradlew test` 전체 PASS.
- [ ] **Step 6: 커밋** — `.gitmessage.txt`를 읽고 `Feat(api) : 텔레그램 클라이언트와 알림 전송기`.

### Task 2: 메시지 포맷과 양조장 항목별 출처

개요의 `(출처)` 링크는 **웹 검색으로 채운 항목에만** 붙는다. 지금 `Brewery`에는 출처 URL이 하나뿐이고, 공공데이터 양조장의 빈 칸을 웹으로 채울 때(`BreweryMatcher.fill`) 어느 칸을 채웠는지와 그 URL을 남기지 않는다. 이 태스크에서 기록을 더한다. 이미 운영 DB에서 채워진 칸은 기록이 없으므로 링크 없이 나온다(허용).

**Files:**
- Create: `db/migration/V7__brewery_web_fields.sql` (`ALTER TABLE brewery ADD COLUMN web_fields VARCHAR(255) NULL, ADD COLUMN web_source_url VARCHAR(1000) NULL;`)
- Modify: `brewery/entity/Brewery.java`, `analysis/brewery/BreweryMatcher.java` (`fill`에서 `recordWebFill` 호출)
- Create: `publish/PublishFailure.java`, `telegram/MessageFormatter.java`
- Test: `brewery/entity/BreweryWebSourceTest.java`, `analysis/brewery/BreweryMatcherTest.java`(추가), `telegram/MessageFormatterTest.java`

**Interfaces:**
- Consumes: `TelegramText`, `InlineButton` (Task 1), `CaptionAssembler.stripMarkers/length`, `AnalysisFailure`.
- Produces:
  - `Brewery.recordWebFill(List<String> fields, String sourceUrl)` (칸 이름은 `fillBlankContact`가 돌려주는 `address` `phone` `homepage` `instagram`. 쉼표로 합쳐 저장하고 기존 값과 합친다. 빈 목록이면 아무것도 하지 않는다), `Optional<String> webSourceOf(String field)` (`source==WEB_SEARCH`이고 값이 있으면 `sourceUrl`, 아니면 `web_fields`에 있으면 `webSourceUrl`, 그 밖은 empty).
  - `enum PublishFailure { IMAGE_FETCH, AUTH, RATE_LIMIT, CAPTION_TOO_LONG, UNKNOWN; String message() }`. 문구는 `docs/telegram-messages.md` 메시지 9 표 그대로.
  - `MessageFormatter`(정적 메서드, 빈 아님): `received(int photoCount)`, `analysisFailed(Post)`, `overview(Post, Brewery breweryOrNull, int photoCount)`, `detailCaption(Post)`, `editLink(String url)`, `canceled(Post)`, `publishing(Post)`, `published(Post)`, `publishFailed(Post)`, `textOnly()`, `tooManyPhotos(int count)`, `tokenRefreshFailed(Instant expiresAt)`, 모두 `TelegramText` 반환. 팝업은 `staleButton()`, `accepted(String act)` (`ok`·`cancel`·`retry`만, 나머지는 null) → `String`. 술 이름은 `post.getDrink().getName()`이다. 술이 없으면(분석 실패 뒤 [수정] 등) 이름과 그 앞 공백을 빼고 `게시에 실패했습니다.`처럼 끝 공백 없이 쓴다.
  - 버튼 배치: 개요 `[[확인 ok][상세 detail][수정 edit][취소 cancel]]` 한 줄. 분석 실패 `[[재시도][수정]]`. 게시 실패는 사유가 `CAPTION_TOO_LONG.message()`면 `[[수정]]`, 아니면 `[[재시도]]`. 콜백 데이터 버전은 `post.getVersion()`.

- [ ] **Step 1: `BreweryWebSourceTest` 작성** — `PUBLIC_DATA` 양조장에 `recordWebFill(["phone"], "https://a")` → `webSourceOf("phone")=="https://a"`, `webSourceOf("address")` empty. `WEB_SEARCH` 양조장(주소 있음, `sourceUrl` "https://b") → `webSourceOf("address")=="https://b"`, 값 없는 `phone`은 empty.
- [ ] **Step 2: `BreweryMatcherTest`에 `blankFillRecordsWebFields` 추가** — 공공데이터 양조장의 전화가 비어 있고 조사 결과가 전화를 주면, 저장 후 `webSourceOf("phone")`가 조사 결과의 `sourceUrl`.
- [ ] **Step 3: `MessageFormatterTest` 작성** — 문서의 예시를 그대로 기대값으로 쓴다(링크는 `<a href>` 형태로).
  - `overviewPublicData`: 메시지 3의 화요 25 예시. 지도 링크 `https://map.naver.com/p/search/` + `URLEncoder.encode(주소, UTF_8)`(공백은 `%20`으로 바꾼다), 버튼 `ok:{id}:{version}` 등 4개.
  - `overviewWebSearchAddsPerFieldSource`: 문배술 예시. 웹 항목 줄 끝에만 ` <a href="{url}">출처</a>`, 전화 줄에는 없다. `otherSns` `https://blog.naver.com/x` → `🔗 <a href="https://blog.naver.com/x">블로그</a>`.
  - `overviewWithoutBrewery`: `⚠ 양조장 미확인` 첫 줄, `(별점 없음)`, 양조장·연락처 줄 없음.
  - `ratingFormat`: 4 → `★4.0`, 4.5 → `★4.5`.
  - `escapesUserValues`: 술 이름 `탁주 <생>&A` → `탁주 &lt;생&gt;&amp;A`. 캡션 원문(`detailCaption`)도 이스케이프하고 `<!--brewery-->`가 남지 않는다.
  - `publishFailedCaptionTooLongHasEditOnly`, `publishedWithoutPermalink`(대체 문구), `tokenRefreshFailedShowsKoreanWeekday`(`2026-12-02(수)`), `tooManyPhotos(12)`, `textOnly`, `staleButton`, `accepted`.
- [ ] **Step 4: 실패 확인** — `./gradlew test --tests '*MessageFormatterTest' --tests '*BreweryWebSourceTest' --tests '*BreweryMatcherTest'`.
- [ ] **Step 5: 구현** — 해시태그 개수는 `post.getHashtags()`의 `#` 개수, 사진 수는 인자. 기타 SNS는 `otherSns`를 쉼표·공백으로 나눠 `http(s)`로 시작하는 것만 쓰고, 호스트로 이름을 정한다(`blog.naver.com` 블로그, `youtube.com`·`youtu.be` 유튜브, `facebook.com` 페이스북, 그 밖 `기타 SNS`).
- [ ] **Step 6: 통과 확인** — `./gradlew test` 전체 PASS.
- [ ] **Step 7: 커밋** — `Feat(api) : 텔레그램 메시지 포맷과 양조장 항목별 출처`.

### Task 3: 게시물 알림 (접수 · 분석 실패 · 개요)

**Files:**
- Create: `telegram/PostNotifier.java`, `post/event/PostPublishFinishedEvent.java`
- Test: `telegram/PostNotifierTest.java`

**Interfaces:**
- Consumes: `TelegramSender`, `MessageFormatter`, `PostReceivedEvent`, `PostAnalyzedEvent`.
- Produces:
  - `interface CaptionLengthGuard { boolean failIfTooLong(Long postId); }` (`publish` 패키지). Task 6의 `PublishService`가 구현한다. 그 전까지는 테스트에서 목으로 쓰고, 빈이 없으면 기동이 깨지므로 이 태스크에서 항상 false를 돌려주는 임시 구현을 두었다가 Task 6에서 지운다.
  - `record PostPublishFinishedEvent(Long postId, PostStatus result)` (`PUBLISHED` 또는 `FAILED`, 트랜잭션 밖에서 발행).
  - `PostNotifier` 공개 메서드(모두 `@Async(TelegramExecutorConfig.EXECUTOR)`): `notifyReceived(Long postId)`, `notifyReviewReady(Long postId)` (P4의 수정 저장도 이것을 부른다), `notifyAnalysisFailed(Long postId)`, `notifyPublishing(Long postId)`, `notifyPublishFinished(Long postId)`, `notifyCanceled(Long postId)`.
  - 리스너: `PostReceivedEvent` → `notifyReceived` (`@TransactionalEventListener(AFTER_COMMIT)`, 웹 공유로 들어온 것도), `PostAnalyzedEvent(PENDING_REVIEW)` → `notifyReviewReady`, `PostAnalyzedEvent(FAILED)` → `notifyAnalysisFailed`, `PostPublishFinishedEvent` → `notifyPublishFinished` (`@EventListener`).
  - `notifyReviewReady` 순서: (1) `CaptionLengthGuard.failIfTooLong(id)`가 true면 개요 대신 끝(가드가 실패 이벤트를 낸다). (2) 상태가 `PENDING_REVIEW`가 아니면 끝. (3) 개요를 `OVERVIEW:{id}:{version}`로 보낸다. (4) 보냈으면 이 게시물의 이전 `OVERVIEW` 메시지 id들의 버튼을 모두 제거한다(방금 보낸 것 제외). 제거 실패는 로그만.

- [ ] **Step 1: `PostNotifierTest` 작성** (Mockito, 실행기는 동기로)
  - `receivedSentOncePerPost`: 같은 postId 두 번 → `TelegramSender.send("RECEIVED:5", …)` 두 번 호출되지만 키가 같다(실제 중복 차단은 Task 1에서 검증).
  - `newOverviewRemovesOldButtons`: 이전 개요 id `[70]`이 있고 새로 77을 보내면 `removeKeyboard(70)`만 호출.
  - `tooLongCaptionSkipsOverview`: 가드가 true → 개요를 보내지 않음.
  - `notPendingSkipsOverview`: 상태 `CANCELED` → 보내지 않음.
  - `analysisFailedUsesVersionKey`: `ANALYSIS_FAILED:5:3`.
- [ ] **Step 2: 실패 확인 → Step 3: 구현** — DB 읽기는 읽기 전용 트랜잭션 안에서 `TelegramText`까지 만든 뒤, 트랜잭션 밖에서 보낸다.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 PASS. 기존 `AnalysisFlowIntegrationTest`가 텔레그램 미설정으로 그대로 통과하는지도 본다.
- [ ] **Step 5: 커밋** — `Feat(api) : 접수·분석 결과 텔레그램 알림`.

### Task 4: 텔레그램 입력 (폴링 · 앨범 묶기 · 접수)

**Files:**
- Create: `telegram/TelegramPoller.java`, `telegram/PollBackoff.java`, `telegram/AlbumCollector.java`, `telegram/TelegramIntake.java`, `common/config/SchedulingConfig.java` (`@EnableScheduling`, Task 5의 토큰 갱신도 쓴다)
- Test: `telegram/AlbumCollectorTest.java`, `telegram/TelegramIntakeTest.java`, `telegram/TelegramPollerTest.java`

**Interfaces:**
- Consumes: `TelegramClient`, `TelegramSender`, `MessageFormatter.textOnly/tooManyPhotos`, `PostIntakeService.receive(PostSource.TELEGRAM, memo, null, List<byte[]>)`, `CallbackHandler.handle(CallbackQuery)` (Task 7. 그 전까지 폴러는 콜백을 로그만 남긴다).
- Produces:
  - `AlbumCollector(Clock)`: `void accept(TelegramMessage m)`, `List<PendingAlbum> drainDue()` (마지막 사진 후 3초가 지난 것), `List<PendingAlbum> drainAll()` (종료할 때). `record PendingAlbum(long chatId, List<String> fileIds, String memo)`. 텍스트만 온 메시지는 `accept`가 `TextOnly` 결과를 돌려준다: `sealed interface Accepted permits Buffered, TextOnly`.
  - 묶는 규칙: `media_group_id`가 같으면 한 앨범. 묶음 없는 사진 한 장도 3초 동안 기다린다(직후 메모를 받으려고). 대기 중에 다른 `media_group_id`의 사진이 오면 이전 앨범은 바로 마감한다. 사진은 `photo` 배열의 마지막(가장 큰) 크기, 또는 `mime_type`이 `image/jpeg`·`image/png`인 `document`. 메모는 사진 캡션(여러 장에 있으면 순서대로 줄바꿈으로 합침) + 3초 안에 온 텍스트(줄바꿈으로 덧붙임). 사진 순서는 `message_id` 오름차순.
  - `TelegramIntake`: `@Scheduled(fixedDelay = 500)`로 `drainDue()`를 처리한다. 11장 이상이면 내려받지 않고 메시지 12. 아니면 한 장씩 `downloadFile`(20MB 넘는 `fileSize`는 받지 않고 그 앨범 접수 실패로 로그) → `PostIntakeService.receive`. `BusinessException`은 경고 로그(사용자 안내 문구는 문서에 없으므로 보내지 않는다).
  - `TelegramPoller`: lottery-picker `bot/service/TelegramPoller.java`·`PollBackoff.java`를 옮긴다. 소유자 chat이 아닌 업데이트는 버린다. `stop()`에서 `drainAll()`을 처리해 대기 중 앨범을 잃지 않는다.

- [ ] **Step 1: `AlbumCollectorTest` 작성** (고정 `MutableClock`)
  - `groupsByMediaGroupId`: 같은 그룹 3장 → 2.9초 뒤 `drainDue()` 비어 있음, 3.0초 뒤 1개, `fileIds` 3개 순서대로, 메모는 첫 장 캡션.
  - `textWithinWindowBecomesMemo`: 사진 후 1초에 텍스트 → `Buffered`, 마감된 앨범의 메모에 붙음.
  - `textAfterWindowIsTextOnly`: 사진 마감 뒤 5초에 텍스트 → `TextOnly`.
  - `textWithoutPhotoIsTextOnly`.
  - `newGroupClosesPrevious`: 그룹 A 사진 후 그룹 B 사진 → A는 즉시 drain 대상.
  - `acceptsImageDocumentIgnoresPdf`.
- [ ] **Step 2: `TelegramIntakeTest` 작성**
  - `elevenPhotosRejectedWithoutDownload`: 11개 → `downloadFile` 0회, `tooManyPhotos(11)` 전송, `receive` 0회.
  - `receivesAsTelegramSource`: 2장 → `receive(TELEGRAM, memo, null, [bytes1, bytes2])`.
- [ ] **Step 3: `TelegramPollerTest` 작성** — `ignoresOtherChat`: 다른 chat_id 메시지는 수집기에 들어가지 않고 아무것도 보내지 않는다. `advancesOffset`: 업데이트 id 10, 11 → 다음 호출 offset 12. `notStartedWhenUnconfigured`.
- [ ] **Step 4: 실패 확인 → Step 5: 구현 → Step 6: `./gradlew test` 전체 PASS.**
- [ ] **Step 7: 커밋** — `Feat(api) : 텔레그램 사진 입력과 앨범 묶기`.

### Task 5: 인스타그램 클라이언트와 장기 토큰

**Files:**
- Modify: `application.yml` (`app.instagram.base-url: https://graph.instagram.com`, `api-version`(Step 1에서 확인한 값), `container-poll-interval: 3s`, `container-timeout: 5m`, `refresh-before: 30d`, `refresh-cron: "0 0 4 * * *"`, `zone: Asia/Seoul`)
- Create: `instagram/InstagramProperties.java`, `instagram/InstagramClient.java`, `instagram/InstagramApiException.java`, `instagram/service/InstagramTokenService.java`, `instagram/controller/InstagramTokenController.java`, `instagram/dto/InstagramTokenResponse.java`
- Modify: `instagram/repository/InstagramTokenRepository.java` (필요하면)
- Test: `instagram/InstagramClientTest.java`, `instagram/service/InstagramTokenServiceTest.java`, `instagram/controller/InstagramTokenControllerTest.java`

**Interfaces:**
- Produces:
  - `InstagramClient`: `String createImageContainer(String token, String imageUrl, boolean carouselItem, String captionOrNull)`, `String createCarouselContainer(String token, List<String> childIds, String caption)`, `ContainerStatus containerStatus(String token, String containerId)` (`enum ContainerStatus { IN_PROGRESS, FINISHED, PUBLISHED, ERROR, EXPIRED }`, `status_code` 필드), `String publish(String token, String containerId)` (media id), `Optional<String> permalink(String token, String mediaId)`, `RefreshedToken refresh(String token)` (`record RefreshedToken(String accessToken, long expiresInSeconds)`). 모든 호출은 `/{api-version}/...`, 사용자 id는 `app.instagram.user-id`.
  - `InstagramApiException(int code, Integer subcode, String message)` + `PublishFailure classify()`: code 190 또는 10, 200~299 → `AUTH`. code 4, 17, 32, 613 또는 subcode 2207042 → `RATE_LIMIT`. subcode 2207052(미디어를 가져오지 못함) 또는 2207003 → `IMAGE_FETCH`. 그 밖 `UNKNOWN`. 코드표는 Step 1에서 공식 문서로 맞춘다.
  - `InstagramTokenService`: `String currentToken()` (없으면 `InstagramApiException` code 190으로 실패 → AUTH), `void initIfMissing()` (`ApplicationReadyEvent`. DB 행이 없고 환경변수가 있으면 먼저 `refresh`를 시도해 실제 만료를 받고, 실패하면(발급 24시간 이내 등) `now+60일`로 저장), `void refreshIfDue()` (`@Scheduled(cron, zone)`. 만료까지 30일 이하이고 마지막 갱신 후 24시간이 지났으면 갱신. 실패하면 `TOKEN_REFRESH_FAILED:{오늘}`로 메시지 11), `TokenStatus reload()` (환경변수 값으로 DB를 덮고 갱신을 한 번 시도. 환경변수가 비면 `BusinessException(INSTAGRAM_TOKEN_MISSING)`).
  - `POST /api/v1/instagram/token/reload` (로그인 필요) → `ApiResponse<InstagramTokenResponse(Instant expiresAt, Instant refreshedAt)>`. `GET /api/v1/instagram/token` 같은 응답. 토큰 값 자체는 응답·로그에 넣지 않는다. P4의 웹 설정 화면이 이 둘을 부른다.
  - `ErrorCode.INSTAGRAM_TOKEN_MISSING(HttpStatus.BAD_REQUEST, "INSTAGRAM_ACCESS_TOKEN 환경변수가 비어 있습니다.")` (`api-conventions` 스킬의 명명 규칙을 확인하고 추가).

- [ ] **Step 1: Meta 공식 문서 확인** — Instagram API with Instagram Login의 Content Publishing(컨테이너 생성 `POST /{ig-user-id}/media`, 캐러셀 `media_type=CAROUSEL`·`children`, `GET /{container-id}?fields=status_code`, `POST /{ig-user-id}/media_publish`), 장기 토큰 갱신(`GET /refresh_access_token?grant_type=ig_refresh_token`), 에러 코드, 현재 API 버전. 다른 값은 이 문서의 해당 줄을 고치고 확인 날짜를 적는다.
- [ ] **Step 2: `InstagramClientTest` 작성** (`@RestClientTest`) — `carouselRequestShape`(자식 `is_carousel_item=true`, 부모 `children`이 쉼표로 이은 id, `caption`), `parsesStatusCode`(`FINISHED`), `errorBodyBecomesException`(`{"error":{"code":190,…}}` → `classify()==AUTH`), `subcode2207042IsRateLimit`, `refreshParsesExpiresIn`. 요청 URL에 토큰이 들어가더라도 예외 메시지·로그에는 넣지 않는다(`exceptionMessageHasNoToken`).
- [ ] **Step 3: `InstagramTokenServiceTest` 작성** (Mockito, 고정 Clock)
  - `initFromEnvWhenEmpty`: 행 없음 + env 있음 + refresh 성공(5,184,000초) → `expiresAt = now+60일`.
  - `initKeepsExistingRow`: 행 있으면 env를 쓰지 않는다.
  - `refreshSkippedWhenFarFromExpiry`: 만료 40일 남음 → 호출 없음.
  - `refreshFailureNotifiesOncePerDay`: 실패 두 번(같은 날) → `send("TOKEN_REFRESH_FAILED:2026-11-16", …)` 키가 같다.
- [ ] **Step 4: `InstagramTokenControllerTest`** (`@WebMvcTest`) — 응답 본문에 `accessToken`이 없다, 미로그인 401.
- [ ] **Step 5: 실패 확인 → Step 6: 구현 → Step 7: `./gradlew test` 전체 PASS.**
- [ ] **Step 8: 커밋** — `Feat(api) : 인스타그램 클라이언트와 장기 토큰 갱신`.

### Task 6: 게시 서비스 (확인 · 재시도 · 복구)

**Files:**
- Create: `db/migration/V8__post_ig_container.sql` (`ALTER TABLE post ADD COLUMN ig_container_id VARCHAR(255) NULL;`)
- Modify: `post/entity/Post.java` (`igContainerId`, `recordContainer(String)`), `post/repository/PostRepository.java`(필요한 조회)
- Create: `publish/PublishService.java`, `publish/PublishWorker.java`, `publish/PublishExecutorConfig.java`, `publish/PublishRecovery.java`, `publish/ConfirmResult.java`
- Test: `publish/PublishServiceTest.java`, `publish/PublishWorkerTest.java`, `publish/PublishRecoveryTest.java`

**Interfaces:**
- Consumes: `InstagramClient`, `InstagramTokenService.currentToken()`, `PhotoService.publicUrl`, `CaptionAssembler`, `PublishFailure`, `CaptionLengthGuard` (Task 3), `PostPublishFinishedEvent`.
- Produces:
  - `enum ConfirmResult { STARTED, STALE, CAPTION_TOO_LONG }`.
  - `PublishService implements CaptionLengthGuard`:
    - `ConfirmResult confirm(Long postId, int version)`: 한 트랜잭션. 상태 `PENDING_REVIEW`·버전 일치가 아니면 `STALE`. 캡션이 2,200자를 넘으면 `recordFailure(PUBLISHING, CAPTION_TOO_LONG.message())` 후 `PENDING_REVIEW → FAILED`(버전 검사), 커밋 뒤 `PostPublishFinishedEvent(FAILED)`, `CAPTION_TOO_LONG`. 아니면 양조장이 있으면 `markVerified()`(사용자가 본 정보) 후 `PENDING_REVIEW → PUBLISHING`(버전 검사, 0건이면 `STALE`), 커밋 뒤 게시 실행기에 `PublishWorker.publish(id)`를 넣고 `STARTED`.
    - `ConfirmResult retry(Long postId, int version)`: 상태 `FAILED`, `failedStage==PUBLISHING`, 사유가 `CAPTION_TOO_LONG`이 아니고 버전이 맞을 때만 `clearFailure()` 후 `FAILED → PUBLISHING`. 아니면 `STALE`.
    - `boolean failIfTooLong(Long postId)`: `PENDING_REVIEW`이고 2,200자 초과면 위와 같이 `FAILED`로 바꾸고 이벤트를 낸 뒤 true.
  - `PublishWorker.publish(Long postId)` (트랜잭션 밖, 예외를 밖으로 던지지 않는다):
    1. 상태가 `PUBLISHING`이 아니면 끝.
    2. 저장된 `igContainerId`가 있으면 상태를 본다. `PUBLISHED` → 다시 올리지 않고 `recordPublished(null, null, now)` 후 `PUBLISHED`(완료 알림은 permalink 대체 문구). `FINISHED` → 4로. `IN_PROGRESS` → 3의 대기로. `ERROR`·`EXPIRED`·조회 실패 → 컨테이너를 새로 만든다.
    3. 컨테이너 생성: 사진 1장이면 단일 이미지 컨테이너(캡션 포함), 2~10장이면 자식 컨테이너를 순서대로 만들고 부모 캐러셀 컨테이너. **부모 id를 publish 전에 짧은 트랜잭션으로 저장한다.** `status_code`를 `container-poll-interval` 간격으로 `container-timeout`까지 본다. `ERROR` → `IMAGE_FETCH`, 시간 초과 → `UNKNOWN`.
    4. `publish` → media id. `permalink`(실패하면 null). `recordPublished(mediaId, permalink, now)` 후 `PUBLISHING → PUBLISHED`. 
    5. 실패하면 `recordFailure(PUBLISHING, reason.message())` 후 `PUBLISHING → FAILED`. 사유는 `InstagramApiException.classify()`, 그 밖 예외는 `UNKNOWN`(스택트레이스 로그). 
    6. 상태를 바꿨으면 `PostPublishFinishedEvent`를 정확히 한 번 낸다.
  - `PublishRecovery`: `ApplicationReadyEvent`에 `PUBLISHING` 게시물을 `FAILED`(`PUBLISHING`, `UNKNOWN`)로 바꾸고 이벤트를 낸다. 컨테이너 id는 남겨서 [재시도] 때 2번 경로로 이어진다.
  - 실행기 `PublishExecutorConfig.EXECUTOR = "publishExecutor"` (스레드 1개).

- [ ] **Step 1: `PublishServiceTest` 작성** (`@DataJpaTest` + `@Import`, 실행기는 목)
  - `confirmTwiceStartsOnce`: 같은 `(id, version)`으로 두 번 → `STARTED`, `STALE`. 실행기 제출 1회.
  - `staleVersionRejected`: 버전 2인 게시물에 `confirm(id, 1)` → `STALE`, 상태 그대로.
  - `captionOver2200Fails`: 마커 제거 후 2,201자 → `CAPTION_TOO_LONG`, 상태 `FAILED`, `failedStage=PUBLISHING`, 사유 `캡션이 2,200자를 넘었습니다.`. 마커를 포함하면 2,201자지만 제거하면 2,200자인 캡션은 `STARTED`.
  - `confirmMarksBreweryVerified`.
  - `retryRejectsCaptionTooLong`, `retryRejectsAnalysisFailure`.
- [ ] **Step 2: `PublishWorkerTest` 작성** (목 `InstagramClient`)
  - `singlePhotoPublishesWithoutCarousel`: 1장 → `createImageContainer(token, url, false, caption)` 하나, 캡션에 `<!--brewery-->` 없음.
  - `carouselKeepsOrder`: 3장 → 자식 URL 순서가 `position` 순서, 부모 생성, `igContainerId` 저장이 `publish` 호출보다 먼저(`InOrder`).
  - `retryWithPublishedContainerDoesNotRepublish`: `igContainerId` 있음 + `PUBLISHED` → `publish` 0회, 상태 `PUBLISHED`.
  - `containerErrorIsImageFetch`, `authErrorClassified`(code 190 → 사유 `인스타그램 인증에 문제가 있습니다. 토큰을 확인해 주세요.`), `timeoutIsUnknown`.
  - `eventOncePerRun`.
- [ ] **Step 3: `PublishRecoveryTest` 작성** — `recoversStuckPublishingOnStartup`: `PUBLISHING` 1건 → `FAILED`, `failedStage=PUBLISHING`, 컨테이너 id 유지, 이벤트 1회.
- [ ] **Step 4: 실패 확인 → Step 5: 구현 → Step 6: `./gradlew test` 전체 PASS.**
- [ ] **Step 7: 커밋** — `Feat(api) : 인스타그램 게시와 중복 방지`.

### Task 7: 버튼 콜백 처리

**Files:**
- Create: `telegram/CallbackHandler.java`, `telegram/CallbackData.java`
- Modify: `telegram/TelegramPoller.java` (콜백을 `CallbackHandler`로 넘김)
- Test: `telegram/CallbackDataTest.java`, `telegram/CallbackHandlerTest.java`

**Interfaces:**
- Consumes: `PublishService.confirm/retry`, `AnalysisService.analyze(id, PostStatus.FAILED)`(분석 실행기에 제출), `PostRepository.transition(id, PENDING_REVIEW, CANCELED, version, now)`, `PostNotifier.notifyPublishing/notifyCanceled`, `TelegramClient.answerCallback/removeKeyboard/sendPhotos`, `TelegramSender`, `MessageFormatter`, `PhotoStorage.locate`, `app.public-base-url`.
- Produces:
  - `record CallbackData(String act, long postId, int version)`, `static Optional<CallbackData> parse(String)` (형식이 다르거나 `act`가 다섯 개 밖이면 empty).
  - `CallbackHandler.handle(CallbackQuery q)`: 소유자 chat이 아니면 아무것도 하지 않는다. 파싱 실패, 게시물 없음, 버전 불일치는 `answerCallback(id, staleButton())`. 그 밖은 동작마다:

| act | 받는 상태 | 처리 | 팝업 | 누른 메시지 버튼 |
|---|---|---|---|---|
| `ok` | `PENDING_REVIEW` | `confirm`. `STARTED` → `notifyPublishing`. `CAPTION_TOO_LONG` → (실패 알림은 이벤트로 감). `STALE` → 오래된 버튼 팝업 | `게시를 시작합니다.` (`STARTED`일 때) | `STALE`이 아니면 제거 |
| `cancel` | `PENDING_REVIEW` | 버전 검사 전이 → `notifyCanceled` | `취소했습니다.` | 제거 |
| `detail` | `PENDING_REVIEW` | 변환본 사진을 게시 순서로 `sendPhotos` + `detailCaption` (키 없음) | 없음 | 그대로 |
| `edit` | `PENDING_REVIEW`, `FAILED` | `editLink("{public-base-url}/posts/{id}/edit")` (키 없음) | 없음 | 그대로 |
| `retry` | `FAILED` | `failedStage==ANALYZING` → 분석 실행기에 `analyze(id, FAILED)`. `PUBLISHING` → `retry`, `STARTED`면 `notifyPublishing` | `다시 시도합니다.` | 제거 |

  - 받는 상태가 아니면 오래된 버튼 팝업. 어떤 경우든 `answerCallback`은 한 번만 보낸다(텔레그램 로딩 표시를 끄려고. 팝업이 없으면 `null` 문구로).
  - `[수정]` 링크 대상 화면(`/posts/{id}/edit`)은 P4에서 만든다. 이 단계에서는 링크만 보낸다.

- [ ] **Step 1: `CallbackDataTest`** — `ok:128:2` 파싱, `ok:128`·`zz:1:1`·`ok:x:1` empty, 64바이트를 넘지 않음(`ok:9223372036854775807:2147483647`).
- [ ] **Step 2: `CallbackHandlerTest`** (Mockito)
  - `ignoresCallbackFromOtherChat`: `answerCallback` 포함 어떤 호출도 없음.
  - `staleVersionShowsPopup`: 문구가 `이미 처리했거나 오래된 버튼입니다. 최신 개요 메시지를 확인해 주세요.`
  - `okStartsPublishAndRemovesButtons`, `okStaleDoesNotRemoveButtons`, `cancelTransitionsAndNotifies`, `detailSendsPhotosInOrderWithoutStateChange`, `editSendsLinkForFailedPost`, `retryAnalysisSubmitsAnalyze`, `retryPublishCallsRetry`, `answersExactlyOnce`(모든 경로).
- [ ] **Step 3: 실패 확인 → Step 4: 구현 → Step 5: `./gradlew test` 전체 PASS.**
- [ ] **Step 6: 커밋** — `Feat(api) : 텔레그램 버튼 처리`.

### Task 8: 실제 봇·계정 검증과 문서 정리

T0(인스타그램 프로페셔널 전환, Meta 앱, 새 텔레그램 봇)이 끝나 있어야 한다. 에이전트는 `.env*`를 읽거나 고치지 않는다.

**Files:**
- Modify: `docs/telegram-messages.md` ("P3에서 구현 예정" 제거, 소스 메서드 시그니처를 Task 2 것으로, 중복 키의 attempt를 version으로 고친 이유 한 줄, 지도 링크 확인 결과), `deploy/README.md` (장기 토큰 재발급 절차, `POST /api/v1/instagram/token/reload`, 토큰 갱신 시각), `docs/superpowers/plans/2026-11-16-review-to-instagram-p3-telegram-publish.md` (확인한 Meta 수치)

- [ ] **Step 1: 사용자에게 실제 봇·계정 검증을 진행할지 확인한다.** 실제 인스타그램 계정에 글이 올라가고 Claude 비용이 든다. 사용자가 로컬 `.env`에 봇 토큰·chat_id를 넣는다.
- [ ] **Step 2: 로컬 텔레그램 검증** — 로컬 서버를 띄우고 사진 1장 + 캡션 메모, 앨범 3장 + 직후 텍스트 메모, 텍스트만, 11장을 보낸다. 접수 → 개요 수신, [상세](앨범·캡션 원문), [수정](링크), [취소](버튼 제거·취소 알림), 오래된 버튼 팝업을 확인한다. 개요의 네이버 지도 링크가 휴대폰에서 열리는지 확인한다. 로컬은 Meta가 이미지를 가져갈 수 없으므로 [확인]은 `인스타그램이 이미지를 가져가지 못했습니다.` 실패와 [재시도]까지만 확인한다.
- [ ] **Step 3: NAS 게시 검증 (사용자가 배포를 요청한 뒤)** — `/release` 절차로 배포한 뒤 실제 계정에 1장, 캐러셀 3장을 게시한다. 완료 알림의 permalink가 열리는지, [확인] 연타에 한 번만 올라가는지 본다. 결과를 이 계획 문서 끝에 표로 적는다. 테스트 게시물은 사용자가 앱에서 지운다.
- [ ] **Step 4: 문서 정리** — 위 Files의 세 문서.
- [ ] **Step 5: 커밋** — `Docs : P3 텔레그램·게시 검증 결과 기록`.

## 확인한 Meta 수치 (2026-11-16)

출처는 Task 5 Step 1에서 읽은 Meta for Developers 공식 문서(콘텐츠 게시, 토큰 갱신, 오류 코드).

- API 버전 `v27.0` (`https://graph.instagram.com/v27.0/...`). `refresh_access_token`만 버전 접두사 없이 `/refresh_access_token`으로 호출한다.
- 캐러셀은 문서상 최대 20개이나 제품은 10장 제한을 유지한다. 24시간 게시 한도는 50건.
- 컨테이너 상태는 문서가 1분에 1회, 최대 5분 조회를 권장해 `container-poll-interval: 60s`, `container-timeout: 5m`으로 구현했다.
- 토큰 갱신은 발급 24시간 이상, 미만료일 때 가능하고 갱신 후 60일 유효하다.
- 오류 코드: 2207042(code 9) 일일 게시 한도, 2207052(code 9004) 미디어 가져오기 실패, 2207003(code -2) 다운로드 시간 초과는 문서에서 확인했다.
- 미확인: permalink 필드(`?fields=permalink`) 명세, 일반 Graph 코드 190/10/200~299/4/17/32/613의 개별 설명.
- 저장된 컨테이너 규칙: 저장된 컨테이너 조회가 Meta code 100(없음)일 때만 새 컨테이너를 만든다. 그 밖의 조회 실패(-1, 속도 제한, 190 등)는 컨테이너 id를 유지한 채 실패 처리한다.
- [확인]은 저장된 컨테이너를 비우고 새로 만든다; [재시도]만 재사용한다.
- Task 8 Step 2·3 (실제 봇·NAS 게시 검증, 네이버 지도 링크 확인, 끝의 검증 결과 표)는 검증 대기 (Task 8 Step 2·3 미실행).

