# 전통주 후기 → 인스타그램 · 1단계 기반 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 문구 문서 초안을 사용자 점검에 올리고, 로그인·게시물 접수·사진 4:5 변환·공개 이미지 URL·NAS 배포까지 동작하게 만든다.

**Architecture:** lottery-picker의 TOTP 세션 인증과 배포 구성(`deploy/`, GHCR 워크플로)을 이식한다. `com.kdrinker.post` 도메인에 엔티티와 상태 전이를 두고, 사진은 `com.kdrinker.media`가 저장·변환·공개 서빙한다. 분석·텔레그램·게시는 이후 계획에서 붙이므로, 접수는 `PostReceivedEvent`만 발행한다.

**Tech Stack:** Spring Boot 4.1.2 / Java 17 / JPA / Flyway / H2(local)·MySQL 8.4(prod), Java2D(ImageIO), `com.drewnoakes:metadata-extractor`(EXIF 회전), Next.js 16 / React 19 / Vitest, Docker Compose, GitHub Actions + GHCR.

**Spec:** `docs/superpowers/specs/2026-11-14-review-to-instagram-design.md`

## 전체 계획 구성

설계의 T0~T9를 독립적으로 동작하는 네 개의 계획으로 나눈다. 이 문서는 1단계다. 2~4단계는 앞 단계를 병합한 뒤 작성한다(앞 단계의 실제 인터페이스를 기준으로 쓰기 위해서).

| 계획 | 설계 태스크 | 끝나면 되는 것 |
|---|---|---|
| **P1 기반 (이 문서)** | T1 초안, T2, T3 | 문구 문서 점검 요청, 로그인, 접수 API, 4:5 변환, 공개 이미지 URL, NAS 배포 |
| P2 분석 | T4, T5 | 양조장 데이터 적재, Claude 라벨 인식·매칭·캡션 → `PENDING_REVIEW` |
| P3 텔레그램·게시 | T6, T7 (**T1 승인 후**) | 봇 입력, 개요·4버튼, 인스타그램 게시, 토큰 갱신 |
| P4 웹·운영 | T8, T9 | 공유 입력, 수정 화면, 기록, 양조장 관리, 백업 |

T0(사전 준비)은 사용자 작업이다. P1의 Task 7(배포)을 하기 전까지 NAS 도메인을 정하고, Meta·텔레그램·Anthropic 키는 P2·P3 전까지 준비한다.

## Global Constraints

- 패키지 매니저 pnpm, 브랜치는 `dev`에서 딴 피처 브랜치 `feature/p1-foundation`. 커밋은 사용자 요청 시에만, `.gitmessage.txt` 형식(`Type(scope) : 제목`).
- 도메인 로직은 `apps/api`에만. 웹은 호출·표시만.
- 응답·에러 코드는 `api-conventions` 스킬. 새 에러 코드는 `ErrorCode` enum에 `<DOMAIN>_<REASON>`, 한국어 완성 문장.
- DTO는 `record`, Entity는 Controller 밖으로 노출 금지, `@Data`/`@Setter` 금지, 생성자 주입만.
- 테스트 이름은 행동 서술(한국어 메서드명), given/when/then, AssertJ, 외부 I/O 모킹, `Clock`·`SecureRandom` 주입.
- 환경변수를 추가하면 `application.yml`, `apps/api/.env.example`, `deploy/.env.example`, `deploy/compose.yml`, `deploy/README.md` 표를 함께 고친다. `.env*` 실파일은 읽거나 고치지 않는다.
- 세션 유지 기간: **90일**. 공개 경로: `/api/v1/health`, `/api/v1/sessions`, `/api/v1/media/**`. 그 외 `/api/**`는 세션 필요.
- 사진: 게시물당 **1~10장**, 장당 **20MB 이하**, JPEG·PNG만(그 외는 `MEDIA_UNSUPPORTED_TYPE`). 변환 결과는 **1080×1350 JPEG**(4:5), 맞춤 방식은 `CROP`(기본) 또는 `PAD`.
- 공개 이미지 토큰: `SecureRandom` 32바이트 → URL-safe Base64(패딩 없음, 43자). URL `/api/v1/media/{token}.jpg`.

## Review Focus

1. **EXIF 회전된 안드로이드 원본 사진** → 세로로 찍은 사진이 옆으로 누운 채 잘리면 안 된다. EXIF Orientation을 적용한 뒤 자른다. (Task 5 테스트)
2. **매우 긴 사진(파노라마, 9:16 스크린샷)과 아주 작은 사진(가로 400px)** → 비율과 상관없이 1080×1350이 나오고, 작은 사진은 늘려서 채운다. 예외가 나면 안 된다. (Task 5 테스트)
3. **HEIC·WebP처럼 지원하지 않는 사진, 확장자는 .jpg인데 내용이 이미지가 아닌 파일** → 500이 아니라 `MEDIA_UNSUPPORTED_TYPE` 400을 반환하고, 게시물은 만들지 않는다. (Task 6 테스트)
4. **추측한 토큰이나 경로 조작으로 미디어 URL에 접근하는 경우**(`../`, 원본 파일명) → 404를 반환하고, 원본 파일은 절대 서빙하지 않는다. (Task 6 테스트)
5. **같은 게시물에 상태 전이가 동시에 두 번 일어나는 경우**(이후 [확인] 연타의 기반) → 조건부 UPDATE로 한 번만 성공해야 한다. (Task 4 테스트)

---

### Task 1: 문구 문서 초안 → 사용자 점검

**Files:**
- Create: `docs/telegram-messages.md`
- Create: `docs/instagram-caption.md`
- Modify: `CLAUDE.md` ("더 읽을 곳"에 두 문서 추가, "항상 지킬 것"에 문구 변경 시 문서 동반 수정 규칙 추가)

**Interfaces:**
- Produces: P3의 `MessageFormatter`·캡션 생성 프롬프트가 따를 확정 문구. 문서 형식은 lottery-picker `docs/telegram-messages.md`의 구조(공통 규칙 표 → 메시지별 "언제 / 중복 키 / 소스 / 템플릿 / 예시")를 따른다.

- [ ] **Step 1: `docs/telegram-messages.md`를 쓴다.** 공통 규칙에는 HTML parse_mode, 이스케이프, 허용 chat_id, 버튼 콜백 데이터 형식 `act:{postId}:{version}`(act ∈ `ok`/`detail`/`edit`/`cancel`/`retry`)을 적는다. 메시지는 다음을 하나씩 템플릿과 예시로 적는다: 접수됨, 분석 중 실패, 개요(양조장 확인/미확인 두 형태, 웹 검색 출처 링크 표기 포함), 상세(앨범 + 캡션 원문), 수정 링크 안내, 취소됨, 게시 중, 게시 완료(permalink), 게시 실패(+[재시도]), 텍스트만 왔을 때 안내, 토큰 갱신 실패. 개요 템플릿은 설계 문서의 예시를 출발점으로 쓴다.
- [ ] **Step 2: `docs/instagram-caption.md`를 쓴다.** 캡션 틀(첫 줄 훅 → 술 소개 → 맛·페어링 → 별점 → 양조장 블록 → 해시태그), 말투(사용자 메모의 표현 유지, 과장 금지), 양조장 블록 템플릿(구분 마커 `<!--brewery-->`는 내부 저장용이고 게시 전 제거), 해시태그 규칙(고정 태그, 술 종류, 지역, 양조장, 총 개수 상한), 2,200자 상한 처리, Claude 프롬프트 요지를 적는다.
- [ ] **Step 3: `CLAUDE.md`를 고친다.** "더 읽을 곳"에 두 문서를 추가하고, "항상 지킬 것"에 "텔레그램·캡션 문구를 바꾸면 `docs/telegram-messages.md`·`docs/instagram-caption.md`를 함께 고친다"를 추가한다.
- [ ] **Step 4: 사용자에게 두 문서를 보여 주고 점검을 요청한다.** 받은 수정을 반영한다. 승인되면 두 문서 상단에 `- 상태: 승인됨 (YYYY-MM-DD)`을 적는다. **승인 전에는 P3를 시작하지 않는다.** P1의 나머지 태스크는 승인을 기다리지 않고 진행한다.
- [ ] **Step 5: 커밋한다(사용자 요청 시).** `Docs : 텔레그램 문구와 캡션 규칙 초안 추가`

### Task 2: TOTP 세션 인증 (api)

**Files:**
- Create (lottery-picker `apps/api/src/main/java/com/lotterypicker/auth/**`에서 패키지만 `com.kdrinker.auth`로 바꿔 이식): `Base32.java`, `TotpSetup.java`, `TotpVerifier.java`, `SessionAuthFilter.java`, `AuthFilterConfig.java`, `controller/SessionController.java`, `dto/SessionCreateRequest.java`, `entity/AuthSession.java`, `entity/AuthState.java`, `repository/AuthSessionRepository.java`, `repository/AuthStateRepository.java`, `service/AuthService.java`
- Create: `apps/api/src/main/java/com/kdrinker/common/config/ClockConfig.java`, `RandomConfig.java` (lottery-picker에서 이식)
- Create: `apps/api/src/main/resources/db/migration/V1__auth.sql` (lottery-picker `V2__auth.sql`과 같은 내용)
- Modify: `common/ErrorCode.java` (`AUTH_INVALID_CODE` 400, `AUTH_LOCKED` 429, lottery-picker 문구), `application.yml` (`app.auth.*`), `application-local.yml` (local은 `app.auth.enabled: false`, lottery-picker와 같게), `apps/api/.env.example` (`TOTP_SECRET`)
- Test: lottery-picker의 대응 테스트(`auth/**Test.java`)를 이식한다. 추가 테스트는 `SessionAuthFilterTest`, `AuthServiceTest`에 넣는다.

**Interfaces:**
- Produces: 세션 쿠키 기반 인증. `SessionAuthFilter`의 공개 경로 목록을 상수로 둔다. `PUBLIC_PATHS = ["/api/v1/health", "/api/v1/sessions"]`, `PUBLIC_PREFIXES = ["/api/v1/media/"]`.

- [ ] **Step 1: lottery-picker 테스트를 이식하고, 다음 새 테스트를 추가한다.**
  - `세션은_90일_뒤_만료된다()`: 고정 `Clock`에서 로그인하면 저장된 `expiresAt == now + 90일`이고, 응답 쿠키 `Max-Age == 7776000`이다.
  - `미디어_경로는_세션_없이_통과한다()`: `/api/v1/media/abc.jpg`를 쿠키 없이 요청하면 필터를 통과한다.
  - `posts_경로는_세션_없으면_401이다()`: `/api/v1/posts`를 쿠키 없이 요청하면 401, `AUTH_REQUIRED`.
- [ ] **Step 2: `./gradlew test`로 테스트가 실패하는지 확인한다.** 클래스가 없어 컴파일에 실패하는 것이 정상이다.
- [ ] **Step 3: 이식한다.** `AuthService.SESSION_LIFETIME`과 `SessionController.COOKIE_LIFETIME`을 `Duration.ofDays(90)`으로 바꾸고, 필터에 미디어 공개 prefix를 추가한다.
- [ ] **Step 4: `./gradlew test`로 전체 테스트가 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `Feat(api) : TOTP 세션 인증 추가`

### Task 3: 로그인 화면 (web)

**Files:**
- Create (lottery-picker에서 이식): `apps/web/app/login/page.tsx`, `apps/web/components/auth/LoginForm.tsx`, `LoginForm.test.tsx`, `apps/web/types/api/auth.ts`
- Modify: `apps/web/components/providers/QueryProvider.tsx` (+ 테스트). `AUTH_REQUIRED`를 받으면 `/login?next=<현재 경로>`로 이동한다. 이미 처리하고 있으면 `next` 파라미터만 추가한다.

**Interfaces:**
- Consumes: `POST /api/v1/sessions { code }` (Task 2).
- Produces: `/login?next=` 처리. 로그인에 성공하면 `next`로 이동하는데, 같은 사이트의 `/`로 시작하는 경로만 허용하고 그 외에는 `/`로 보낸다. P4의 공유 화면이 이것을 쓴다.

- [ ] **Step 1: 테스트를 쓴다.** lottery-picker `LoginForm.test.tsx`를 이식하고 다음을 추가한다.
  - `it("redirects to next after login")`: `next=/share`이면 `/share`로 이동한다.
  - `it("ignores external next")`: `next=//evil.com`이나 `next=https://x`이면 `/`로 이동한다.
- [ ] **Step 2: `pnpm test`로 실패하는지 확인한다.**
- [ ] **Step 3: 이식하고 `next`를 처리한다.**
- [ ] **Step 4: `pnpm lint && pnpm test`로 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `Feat(web) : 로그인 화면 추가`

### Task 4: 게시물 도메인과 상태 전이

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V2__post.sql`
- Create: `com/kdrinker/brewery/entity/Brewery.java`, `BrewerySource.java`(enum `PUBLIC_DATA`/`WEB_SEARCH`/`MANUAL`), `brewery/repository/BreweryRepository.java`
- Create: `com/kdrinker/drink/entity/Drink.java`, `drink/repository/DrinkRepository.java`
- Create: `com/kdrinker/post/entity/Post.java`, `PostPhoto.java`, `PostStatus.java`, `PostSource.java`(`TELEGRAM`/`SHARE`), `FitMode.java`(`CROP`/`PAD`), `post/repository/PostRepository.java`, `PostPhotoRepository.java`
- Create: `com/kdrinker/instagram/entity/InstagramToken.java`, `instagram/repository/InstagramTokenRepository.java`
- Test: `post/entity/PostStatusTest.java`, `post/repository/PostRepositoryTest.java` (`@DataJpaTest`)

**Interfaces:**
- Produces:
  - `enum PostStatus { RECEIVED, ANALYZING, PENDING_REVIEW, PUBLISHING, PUBLISHED, FAILED, CANCELED; boolean canTransitionTo(PostStatus next) }`
  - `Post`: `id`, `drink`(nullable), `rating`(`BigDecimal` 0.0~5.0, 0.5 단위, nullable), `memo`, `tasteNotes`, `pairing`, `caption`, `captionEdited`(boolean), `hashtags`, `status`, `failedStage`(`PostStatus`, nullable), `failureReason`, `version`(int, 0부터), `source`, `telegramMessageId`(Long), `igMediaId`, `permalink`, `createdAt`, `updatedAt`, `publishedAt`. 생성은 `Post.receive(PostSource source, String memo, BigDecimal rating, Clock clock)`.
  - `PostPhoto`: `id`, `post`, `position`(0부터), `originalPath`, `renderedPath`, `publicToken`(43자, unique), `focusX/focusY/focusW/focusH`(`Double` 0~1, nullable), `fitMode`.
  - `Brewery`: `name`, `normalizedName`(index), `address`, `phone`, `homepage`, `instagramHandle`, `otherSns`, `source`, `sourceUrl`, `verified`. `Drink`: `name`, `normalizedName`(index), `aliases`, `category`, `abv`, `volumeMl`, `brewery`.
  - `InstagramToken`: 단일 행(`id=1`), `accessToken`, `expiresAt`, `refreshedAt`.
  - `PostRepository.transition(Long id, PostStatus from, PostStatus to, Instant now): int`. 조건부 UPDATE(`where id=? and status=?`)로 `version`을 +1 하고, 바뀐 행 수를 돌려준다.
  - 허용 전이: `RECEIVED→ANALYZING`, `ANALYZING→PENDING_REVIEW`, `PENDING_REVIEW→PUBLISHING|CANCELED|PENDING_REVIEW(수정)`, `PUBLISHING→PUBLISHED`, `RECEIVED|ANALYZING|PUBLISHING→FAILED`, `FAILED→ANALYZING|PUBLISHING|PENDING_REVIEW`.

- [ ] **Step 1: 테스트를 쓴다.**
  - `PostStatusTest.허용된_전이만_true다()`: 위 허용 전이 표 전부 true, `PUBLISHED→아무거나`와 `CANCELED→아무거나`는 false, `PENDING_REVIEW→PUBLISHED`는 false.
  - `PostRepositoryTest.상태가_맞을_때만_전이하고_버전을_올린다()`: `PENDING_REVIEW`인 게시물에 `transition(id, PENDING_REVIEW, PUBLISHING)`을 하면 1을 돌려주고 `version`이 1 오른다. 다시 같은 호출을 하면 0을 돌려준다.
  - `PostRepositoryTest.사진은_position_순서로_조회된다()`.
- [ ] **Step 2: `./gradlew test`로 실패하는지 확인한다.**
- [ ] **Step 3: 엔티티와 `V2__post.sql`을 구현한다.** H2(MySQL 모드)와 MySQL 8.4 양쪽에서 동작하는 DDL로 쓴다. 문자열 길이는 `caption` 4000, `memo` 2000, `failure_reason` 1000, 그 외 255.
- [ ] **Step 4: `./gradlew test`로 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `Feat(api) : 게시물·양조장·술 도메인 추가`

### Task 5: 사진 4:5 변환

**Files:**
- Modify: `apps/api/build.gradle` (`implementation 'com.drewnoakes:metadata-extractor:<설치 시점 최신 2.x>'`)
- Create: `com/kdrinker/media/PhotoTransformer.java`, `media/FocusBox.java`(record `x, y, w, h`, 0~1), `media/ImageDecoder.java`
- Test: `media/PhotoTransformerTest.java`, `media/ImageDecoderTest.java`

**Interfaces:**
- Produces:
  - `ImageDecoder.decode(byte[] bytes): BufferedImage`. JPEG·PNG만 받는다. 내용을 보고 판별하며 확장자는 보지 않는다. EXIF Orientation(1~8)을 적용한 이미지를 돌려준다. 그 외 형식이거나 깨진 파일이면 `BusinessException(MEDIA_UNSUPPORTED_TYPE)`.
  - `PhotoTransformer.render(BufferedImage src, FocusBox focus /*nullable*/, FitMode mode): byte[]`. 1080×1350 JPEG(품질 0.9)를 돌려준다.
  - `ErrorCode.MEDIA_UNSUPPORTED_TYPE(400, "JPEG 또는 PNG 사진만 올릴 수 있습니다.")`

`CROP` 알고리즘(테스트로 다 정해지지 않는 부분):
```
목표 비율 r = 4/5.
원본 비율이 r보다 넓으면 높이를 다 쓰고 폭 = H*r, 좁으면 폭을 다 쓰고 높이 = W/r.
자를 창의 중심 = focus가 있으면 focus 중심(픽셀), 없으면 원본 중심.
창이 원본 밖으로 나가면 경계 안으로 밀어 넣는다(clamp).
자른 영역을 1080×1350으로 bicubic 리샘플(작으면 확대).
```
`PAD`: 원본을 1080×1350 안에 비율을 유지해 맞춘다(contain). 배경은 같은 원본을 cover로 채운 뒤 흐리게 처리한 이미지다(축소 → 확대 방식이면 충분하다).

- [ ] **Step 1: 테스트를 쓴다.**
  - `ImageDecoderTest.EXIF_방향_6이면_90도_회전한다()`: 테스트 리소스 `exif-orientation-6.jpg`(가로 400×300으로 저장되고 Orientation=6)를 디코드하면 300×400이다. 리소스는 테스트 안에서 생성하거나 작은 파일로 커밋한다.
  - `ImageDecoderTest.PNG는_받는다()`, `ImageDecoderTest.WebP나_텍스트는_MEDIA_UNSUPPORTED_TYPE이다()`: `"hello".getBytes()`와 WebP 매직바이트(`RIFF....WEBP`)로 확인한다.
  - `PhotoTransformerTest.어떤_비율이든_1080x1350이다()`: 4000×3000, 1080×1920, 3000×500, 400×300 각각 `CROP`·`PAD` → 디코드 결과가 1080×1350이다.
  - `PhotoTransformerTest.focus를_중심으로_자른다()`: 1000×500 이미지에서 왼쪽 절반은 빨강, 오른쪽 절반은 파랑이다. `focus=(0.75,0.25,0.2,0.5)`로 `CROP`하면 결과 중앙 픽셀이 파랑이다.
  - `PhotoTransformerTest.focus가_가장자리면_경계_안으로_밀어_넣는다()`: `focus=(0.95,...)`여도 예외 없이 1080×1350이고 오른쪽 끝 픽셀이 원본 오른쪽 끝 색이다.
- [ ] **Step 2: `./gradlew test`로 실패하는지 확인한다.**
- [ ] **Step 3: 구현한다.**
- [ ] **Step 4: `./gradlew test`로 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `Feat(api) : 사진 4:5 변환 추가`

### Task 6: 게시물 접수 API와 공개 미디어

**Files:**
- Create: `com/kdrinker/media/PhotoStorage.java`, `media/controller/MediaController.java`, `media/service/PhotoService.java`
- Create: `com/kdrinker/post/controller/PostController.java`, `post/dto/PostCreateResponse.java`, `post/service/PostIntakeService.java`, `post/event/PostReceivedEvent.java`
- Modify: `common/ErrorCode.java` (`POST_PHOTO_COUNT`, `POST_PHOTO_TOO_LARGE`, `POST_INVALID_RATING`), `application.yml` (`app.media.dir: ${MEDIA_DIR:./.data/media}`, `spring.servlet.multipart.max-file-size: 20MB`, `max-request-size: 210MB`), `apps/api/.env.example` (`MEDIA_DIR`), `apps/api/.gitignore` (`.data/`)
- Test: `post/controller/PostControllerTest.java`(`@WebMvcTest`), `post/service/PostIntakeServiceTest.java`, `media/controller/MediaControllerTest.java`, `media/PhotoStorageTest.java`

**Interfaces:**
- Consumes: `ImageDecoder`, `PhotoTransformer`(Task 5), `Post.receive`, 리포지토리(Task 4), `SecureRandom` 빈(Task 2).
- Produces:
  - `POST /api/v1/posts` (multipart: `photos` 1~10개, `memo` 선택·2000자 이하, `rating` 선택 0~5·0.5 단위, `source` 필수 `TELEGRAM|SHARE`) → 201 `PostCreateResponse(Long id, String status)`.
  - `PostIntakeService.receive(PostSource source, String memo, BigDecimal rating, List<byte[]> photos): Post`. 텔레그램 봇(P3)도 이 메서드를 직접 호출한다. 모든 사진을 먼저 디코드해 검증한 뒤 저장하므로, 하나라도 실패하면 아무것도 저장하지 않는다. 원본을 저장하고 `CROP`·focus 없음으로 렌더링한 뒤, 트랜잭션 커밋 후 `PostReceivedEvent(Long postId)`를 발행한다.
  - `PhotoService.rerender(Long photoId, FocusBox focus, FitMode mode)`. P2(focus 반영)와 P4(여백 넣기 전환)가 쓴다. 공개 토큰은 그대로 두고 렌더링 파일만 바꾼다.
  - `PhotoService.publicUrl(PostPhoto photo): String`. `${app.public-base-url}/api/v1/media/{token}.jpg`를 돌려준다. `app.public-base-url: ${PUBLIC_BASE_URL:http://localhost:3000}`.
  - `GET /api/v1/media/{token}.jpg`. 토큰이 `^[A-Za-z0-9_-]{43}$`와 일치하고 DB에 있을 때만 렌더링 파일을 `image/jpeg`, `Cache-Control: public, max-age=31536000, immutable`로 응답한다. 그 외는 404이고 원본은 서빙하지 않는다.
  - `PhotoStorage`: `{MEDIA_DIR}/original/{postId}/{position}.{jpg|png}`, `{MEDIA_DIR}/rendered/{token}.jpg`. 경로는 내부에서 조립하고, 사용자 입력 문자열을 경로에 쓰지 않는다.
  - 에러 코드: `POST_PHOTO_COUNT(400, "사진은 1장 이상 10장 이하로 올려 주세요.")`, `POST_PHOTO_TOO_LARGE(400, "사진 한 장의 크기는 20MB 이하여야 합니다.")`, `POST_INVALID_RATING(400, "별점은 0에서 5 사이, 0.5 단위로 입력해 주세요.")`. multipart 크기 초과 예외는 `GlobalExceptionHandler`에서 `POST_PHOTO_TOO_LARGE`로 변환한다.

- [ ] **Step 1: 테스트를 쓴다.**
  - `PostControllerTest.사진_3장이면_201과_RECEIVED다()`.
  - `PostControllerTest.사진_0장이나_11장이면_POST_PHOTO_COUNT다()`(경계: 1장·10장은 201).
  - `PostControllerTest.별점_5_5나_4_3이면_POST_INVALID_RATING이다()`(경계: 0, 5, 4.5는 통과).
  - `PostIntakeServiceTest.하나라도_디코드에_실패하면_아무것도_저장하지_않는다()`: 두 번째 사진이 텍스트면 `MEDIA_UNSUPPORTED_TYPE`이고, `PostRepository.save`와 `PhotoStorage` 쓰기가 호출되지 않는다.
  - `PostIntakeServiceTest.접수하면_PostReceivedEvent를_발행한다()`.
  - `MediaControllerTest.있는_토큰이면_jpeg를_준다()`, `MediaControllerTest.없는_토큰이나_형식이_틀린_토큰이면_404다()`: `abc`, `..%2F..%2Fetc%2Fpasswd`, 42자로 확인한다.
  - `PhotoStorageTest.원본과_렌더링_경로가_MEDIA_DIR_안에_있다()`(`@TempDir`).
- [ ] **Step 2: `./gradlew test`로 실패하는지 확인한다.**
- [ ] **Step 3: 구현한다.** 이벤트는 `ApplicationEventPublisher`로 발행한다. 리스너는 P2에서 만든다.
- [ ] **Step 4: `./gradlew test`로 통과하는지 확인한다.**
- [ ] **Step 5: 커밋한다.** `Feat(api) : 게시물 접수와 공개 미디어 추가`

### Task 7: NAS 배포 구성

**Files:**
- Create (lottery-picker `deploy/`에서 이식해 이름을 바꾼다): `deploy/compose.yml`, `deploy/compose.build.yml`, `deploy/.env.example`, `deploy/backup.sh`, `deploy/restore-check.sh`, `deploy/README.md`
- Create: `.github/workflows/deploy.yml` (이미지 `ghcr.io/withbbang/k-drinker-{api,web}`)
- Create (lottery-picker에 있으면 이식): `apps/api/Dockerfile`, `apps/web/Dockerfile`
- Modify: `CLAUDE.md` (환경변수 규칙에 deploy 파일 목록을 명시. lottery-picker 문장과 같게)

**Interfaces:**
- Consumes: `MEDIA_DIR`, `PUBLIC_BASE_URL`, `TOTP_SECRET`, DB 변수.
- Produces: 프로젝트 이름 `k-drinker`, DB 이름 `kdrinker`, NAS 경로 `/volume1/docker/k-drinker`, web 호스트 포트 `127.0.0.1:13001`(lottery-picker 13000과 겹치지 않게), api 볼륨 `media-data:/data/media`(`MEDIA_DIR=/data/media`). P3에서 쓸 `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `ANTHROPIC_API_KEY`, `INSTAGRAM_ACCESS_TOKEN`, `INSTAGRAM_USER_ID`를 compose와 `.env.example`에 빈 기본값으로 미리 넣어 둔다. 환경변수 표에는 "P2/P3에서 사용"이라고 적는다.

- [ ] **Step 1: 이식한다.** `deploy/README.md`에 다음을 추가한다.
  - DSM 역방향 프록시에 새 서브도메인 → `127.0.0.1:13001`을 등록하는 절차.
  - `backup.sh`가 `media-data` 볼륨의 `original/`·`rendered/`도 tar로 함께 백업한다(14개 보관).
  - 외부 이미지 URL 확인 절차.
- [ ] **Step 2: 로컬에서 확인한다.** `docker compose -f deploy/compose.yml -f deploy/compose.build.yml --env-file <임시 env> up -d --build`를 실행한다. 그다음 `curl -fsS http://127.0.0.1:13001/api/v1/health`가 `{"success":true,...}`를 반환하는지 확인한다(curl은 `ctx_execute` shell로 실행한다).
- [ ] **Step 3: 커밋한다.** `Chore : NAS 배포 구성 추가`
- [ ] **Step 4: 사용자와 함께 NAS에 처음 배포한다(병합·푸시 요청 시).** 완료 기준은 두 가지다. 외부 HTTPS로 `/api/v1/health`가 열리고, 로그인 후 `POST /api/v1/posts`로 올린 사진의 `/api/v1/media/{token}.jpg`가 **휴대폰 데이터망에서** 열려야 한다(Meta가 가져갈 수 있는지 확인).

---

## 병합 전

- `/code-review medium feature/p1-foundation`을 제안한다(인증, 파일 서빙, 배포가 함께 들어가 위험도가 높다). 승인받은 뒤 실행하고, 통과 기록 → `/claude-md-management:revise-claude-md` → 병합 요청을 기다린다.
- 병합 후 P2 계획을 작성한다.
