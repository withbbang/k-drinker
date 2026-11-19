# 나머지 화면 구현 계획 (4단계)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 목록·게시물 개요·양조장 목록/상세/추가·설정·로그인·메뉴에 3단계의 옹기 토큰과 공용 부품을 입히고, 점검에서 나온 사용성 문제를 고친다.

**Architecture:** 웹만 바뀐다(API 변경 없음). 맨 위 화면(기록·양조장·설정)은 화면 아래 탭(`AppNav`)을, 세부 화면(후기·양조장 상세/추가·수정)은 3단계의 `TopBar`(뒤로·제목·오른쪽 동작)를 쓴다. 버튼은 `components/ui/button.ts`의 `buttonClass`, 색은 `@theme` 토큰만 쓴다. 라우트는 바꾸지 않는다.

**Tech Stack:** Next.js 16 / React 19 / Tailwind v4 / React Query / Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-11-19-web-redesign-design.md` (4단계 절, 3단계 디자인 토큰·원칙, 제약)

## 점검 뒤 사용자 결정 (2026-11-19)

- 메뉴: 화면 아래 탭(기록·양조장·설정). 로그아웃은 설정 화면으로 옮긴다. 세부 화면은 탭 대신 뒤로 가기 상단 바.
- 게시물 개요 사진: 4:5 비율로 옆으로 넘기고 "1 / N"을 표시한다.
- 로그인: 6자리를 다 입력하면 바로 로그인을 요청한다.

## Global Constraints

- 브랜치 `feat/remaining-screens`(`dev`에서 딴다). 바뀌는 앱은 `apps/web`뿐이다. 커밋 전 `cd apps/web && pnpm lint && pnpm test` 통과.
- pnpm만 쓴다. 새 의존성 없음(`cva` 도입 안 함, `buttonClass` 유틸 유지).
- 커밋 메시지는 `.gitmessage.txt`를 읽고 `Type(web) : 제목`(50자 이내), 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `.claude/rules/web.md`: Tailwind만, JSX `style={}` 금지, `div onClick` 금지, API 호출은 `lib/api.ts` 래퍼, effect 안 setState 금지(렌더에서 파생).
- 색은 토큰 유틸만(`bg-bg` `bg-surface` `border-line` `text-text` `text-muted` `bg-accent` `text-accent` `text-on-accent` `text-danger`). `neutral-*` `red-*` `green-*` `bg-white` `text-white` 클래스는 남기지 않는다(Task 7이 테스트로 막는다).
- 버튼은 `buttonClass("primary" | "secondary" | "text", extra?)`. 주 버튼 알약형, 입력칸 `rounded-field`, 썸네일 `rounded-thumb`. 그림자 대신 `line` 테두리.
- 글꼴: 술 이름(화면의 주인공 텍스트)에만 `font-serif`(고운바탕), 나머지 Pretendard.
- 움직임은 사용자 조작 반응만, `motion-safe:` 변형으로. 불러오는 중 뼈대의 `animate-pulse`도 `motion-safe:animate-pulse`.
- 화면에 개발용 이름(상태 코드 `PENDING_REVIEW` 등, "환경변수")을 보이지 않는다.
- 문구는 3단계와 같은 말투(“~어요/~해요”). 아래 계획에 적힌 문구는 그대로 쓴다.
- 기존 테스트는 접근성 이름·문구로 요소를 찾는다. 이름이 바뀐 버튼(확인→게시하기, 취소→게시물 취소, 확인창 취소→돌아가기)과 옮긴 로그아웃은 테스트를 새 동작으로 다시 쓴다.
- 스타일을 다루는 태스크의 구현자는 시작 전에 `frontend-design:frontend-design`과 `taste-skill:taste-skill` 스킬을 읽고 따른다(사용자 지정 스킬). 이 계획의 토큰·문구가 스킬의 일반 권고보다 우선한다.

## Review Focus

1. **아래 탭이 내용을 가림**: 목록 맨 아래 항목·페이지 이동 버튼이 탭 뒤로 숨지 않아야 한다(아이폰 홈 막대 영역 포함) → Task 1에서 탭을 쓰는 화면의 `main`에 아래 여백 클래스가 있는지 테스트.
2. **로그인 자동 요청이 두 번 나감**: 6자리를 붙여넣은 뒤 Enter를 누르거나, 요청 중에 같은 6자리가 다시 들어와도 요청은 한 번이어야 한다 → Task 2 테스트.
3. **틀린 코드 뒤 다시 입력**: 틀린 6자리로 실패한 뒤 한 자리 지우고 다시 채우면 다시 자동 요청돼야 한다 → Task 2 테스트.
4. **사진 넘기기와 "1 / N" 불일치**: 옆으로 넘기면 번호가 따라 바뀌고, 사진이 한 장이면 번호를 숨긴다 → Task 4 테스트.
5. **새 양조장 저장 뒤 다시 저장하면 중복 생성**: 저장하면 상세 주소로 옮겨 이후 저장은 수정이 된다 → Task 6 테스트.

---

### Task 1: 화면 아래 탭과 로그아웃 이동

**Files:**
- Modify: `apps/web/components/layout/AppNav.tsx`, `apps/web/components/layout/AppNav.test.tsx`, `apps/web/app/layout.tsx`, `apps/web/app/page.tsx`, `apps/web/app/breweries/page.tsx`, `apps/web/app/settings/page.tsx`, `apps/web/app/posts/[id]/edit/page.tsx`
- Create: `apps/web/components/settings/LogoutButton.tsx`, `apps/web/components/settings/LogoutButton.test.tsx`

**Interfaces:**
- Produces: `AppNav()` — 화면 아래 고정 탭. 탭을 쓰는 화면의 `main`은 `TAB_SAFE_PADDING` 상수 클래스를 붙인다. `export const TAB_SAFE_PADDING = "pb-[calc(4rem+env(safe-area-inset-bottom))]"` (AppNav.tsx에서 export). `LogoutButton()` — 확인창을 거쳐 `useLogout()`을 부르는 버튼.

- [ ] **Step 1: 테스트 다시 쓰기**
  - `AppNav.test.tsx`: 기존 "marks the current page" 유지(링크 3개 기록·양조장·설정, 현재 화면 `aria-current="page"`). `/breweries/12`처럼 하위 주소에서도 "양조장" 탭이 현재로 표시되는 테스트 추가(규칙: `href === "/" ? pathname === "/" : pathname.startsWith(href)`). "로그아웃" 버튼이 없다는 테스트 추가. 로그아웃 테스트 4개는 `LogoutButton.test.tsx`로 옮긴다(버튼 이름 "로그아웃", 확인창 제목 "로그아웃할까요?", 확인창 버튼 "로그아웃"·"취소" 그대로).
  - `app/page.tsx`·`breweries/page.tsx`·`settings/page.tsx`의 `main` 클래스에 `TAB_SAFE_PADDING`이 들어가는지는 페이지를 렌더하지 말고 소스 문자열 검사로 본다: `AppNav.test.tsx`에서 세 파일을 `fs`로 읽어 `TAB_SAFE_PADDING`을 import·사용하는지 확인.
- [ ] **Step 2:** `cd apps/web && pnpm test components/layout components/settings/LogoutButton.test.tsx` → FAIL.
- [ ] **Step 3: 구현**
  - `AppNav`: `<nav aria-label="주 메뉴">`를 `fixed inset-x-0 bottom-0 z-30 border-t border-line bg-bg pb-[env(safe-area-inset-bottom)]`로. 안은 `mx-auto flex max-w-sm`의 같은 너비 탭 3개, 각 탭 `min-h-16` 세로 배치(아이콘 24px + 글자 `text-xs`). 현재 탭 `text-accent`, 나머지 `text-muted`. 아이콘은 인라인 SVG(`aria-hidden`, stroke 2, `currentColor`): 기록=잔(술잔), 양조장=집, 설정=톱니. 로그아웃·확인창 코드는 제거.
  - `app/layout.tsx` `viewport`에 `viewportFit: "cover"` 추가(홈 막대 영역 값이 잡히게).
  - 세 맨 위 화면: `AppNav`를 `main` 뒤로 옮기고 `main`에 `TAB_SAFE_PADDING`. 제목 `h1`은 유지.
  - `settings/page.tsx`: 토큰 패널 아래 `LogoutButton`을 `buttonClass("secondary", "w-full")`로.
  - `posts/[id]/edit/page.tsx`: `AppNav`와 `Suspense` 감싼 것을 지운다(상단 바만 남김).
  - `posts/[id]/page.tsx`·`breweries/[id]/page.tsx`·`breweries/new/page.tsx`의 `AppNav`는 Task 4·6에서 지운다.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 화면 아래 탭과 설정의 로그아웃`

### Task 2: 로그인

**Files:**
- Modify: `apps/web/app/login/page.tsx`, `apps/web/components/auth/LoginForm.tsx`, `apps/web/components/auth/LoginForm.test.tsx`

- [ ] **Step 1: 실패하는 테스트** (`LoginForm.test.tsx`에 추가)
  - `"6자리를_채우면_바로_요청한다"`: 입력칸에 `"123456"` 입력 → 버튼을 누르지 않아도 `apiFetch`가 `/api/v1/sessions`, body `{"code":"123456"}`로 1번 불림.
  - `"자동_요청_뒤_Enter를_눌러도_한_번만_보낸다"`: 응답을 끝나지 않는 promise로 두고 `"123456{Enter}"` → 호출 1번.
  - `"실패한_뒤_지우고_다시_채우면_다시_요청한다"`: 첫 호출 거절(ApiError) → `{Backspace}` 후 `"7"` → 호출 2번, 두 번째 body `{"code":"123457"}`.
  - 기존 "submits the six-digit code…"는 버튼 클릭 대신 입력만으로 요청되게 고친다. "disables submit until six digits…"는 유지.
- [ ] **Step 2:** `pnpm test components/auth` → FAIL.
- [ ] **Step 3: 구현**
  - `onChange`에서 정리한 값이 6자리가 되는 순간(이전 값은 6자리가 아님)이고 `!login.isPending`이면 `login.mutate({ code: next })`. effect를 쓰지 않는다. `handleSubmit`도 `login.isPending`이면 아무것도 안 한다.
  - 다시 입력하기 시작하면(값이 바뀌면) 이전 오류를 지운다: `onChange`에서 `login.reset()` (pending이 아닐 때).
  - 버튼은 `buttonClass("primary", "w-full")`, 글자는 항상 "로그인", 요청 중에는 `disabled`만 건다(자동 요청이 실패했을 때 다시 누르는 용도로 남긴다).
  - 페이지 안내문 `text-neutral-600` → `text-muted`. 제목 위에 앱 이름 "k-drinker"를 `font-serif text-accent` 한 줄로 둔다.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 로그인 자동 확인과 새 색`

### Task 3: 기록 목록과 페이지 이동

**Files:**
- Modify: `apps/web/components/posts/PostList.tsx`, `apps/web/components/posts/PostList.test.tsx`, `apps/web/components/ui/Pager.tsx`, `apps/web/components/ui/Pager.test.tsx`

**Interfaces:**
- Produces: `components/ui/Skeleton.tsx` — `export function Skeleton({ className }: { className: string })` (`motion-safe:animate-pulse rounded-thumb bg-surface` + className, `aria-hidden`). Task 4·5·6이 쓴다.

- [ ] **Step 1: 실패하는 테스트**
  - `PostList.test.tsx`: `"손볼_상태는_강조해서_보인다"` — `PENDING_REVIEW` 행의 "검토 대기"와 `FAILED` 행의 "실패"가 각각 `data-tone="action"`·`data-tone="danger"`, `PUBLISHED`의 "게시됨"은 `data-tone="muted"`.
  - `"술_이름은_고운바탕이다"` — 술 이름 요소에 `font-serif` 클래스.
  - `"기록이_없으면_올리는_방법을_안내한다"` — 빈 목록이면 "아직 기록이 없어요." 와 "휴대폰 사진 앱에서 k-drinker로 공유하거나 텔레그램 봇에 사진을 보내면 여기에 쌓여요."가 보인다.
  - `"불러오는_중에는_뼈대를_보인다"` — pending이면 `role="status"`·이름 "불러오는 중"인 요소가 있다.
  - `Pager.test.tsx`: 기존 테스트 유지(이름 "이전"·"다음", 비활성 `aria-disabled`).
- [ ] **Step 2:** `pnpm test components/posts components/ui/Pager.test.tsx` → FAIL.
- [ ] **Step 3: 구현**
  - 상태 표시: `STATUS_TONE: Record<string, "action" | "danger" | "muted">` (`PENDING_REVIEW`→action, `FAILED`→danger, 나머지 muted). action은 `rounded-full bg-accent px-2 text-on-accent text-xs font-semibold`, danger는 `text-danger text-xs font-semibold`, muted는 `text-muted text-xs`. 알 수 없는 상태 코드는 "기타"로 보인다(코드 노출 금지).
  - 행: 썸네일 `w-14 aspect-[4/5] rounded-thumb object-cover`(자리표시도 같은 크기·`bg-line`), 술 이름 `font-serif text-lg`, 별점 `text-accent`(★)와 숫자. 행 테두리는 `border-line`, 누를 때 `active:bg-line`.
  - 불러오는 중: `<div role="status" aria-label="불러오는 중">` 안에 행 모양 `Skeleton` 4개.
  - `Pager`: `buttonClass("secondary")` 링크, 비활성은 같은 클래스에 `opacity-40`, 가운데 숫자 `text-muted tabular-nums`.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 기록 목록 새 구성`

### Task 4: 게시물 개요

**Files:**
- Modify: `apps/web/app/posts/[id]/page.tsx`, `apps/web/components/post-overview/PostOverview.tsx`, `apps/web/components/post-overview/PostOverview.test.tsx`
- Create: `apps/web/components/post-overview/PhotoCarousel.tsx`, `apps/web/components/post-overview/PhotoCarousel.test.tsx`

**Interfaces:**
- Consumes: `TopBar({ title, backHref, actions })`, `buttonClass`, `Skeleton`(Task 3), `ConfirmDialog`(`children`으로 본문).
- Produces: `PhotoCarousel({ photos, version }: { photos: PostPhotoResponse[]; version: number })`.

- [ ] **Step 1: 실패하는 테스트**
  - `PhotoCarousel.test.tsx`:
    - `"사진마다_4:5로_그리고_번호를_보인다"` — 사진 3장 → `img` 3개(alt "사진 1 / 3" … ), 각 `img`의 부모가 `aspect-[4/5]`, 번호 "1 / 3".
    - `"넘기면_번호가_바뀐다"` — 목록 요소(`role="region"`, 이름 "사진")의 `clientWidth`를 300으로 정의하고 `scrollLeft = 300` 뒤 `scroll` 이벤트 → "2 / 3".
    - `"한_장이면_번호를_숨긴다"` — 1장 → "1 / 1" 없음.
  - `PostOverview.test.tsx` 고치기:
    - 버튼 이름 "확인" → "게시하기", 목록의 "취소" → "게시물 취소". 게시 확인창 버튼 "게시하기"·"돌아가기", 취소 확인창 버튼 "게시물 취소"·"돌아가기"와 본문 "취소하면 되돌릴 수 없어요.".
    - "수정" 링크는 상단 바 안에 있다(`within(screen.getByRole("banner"))`).
    - 상단 바 제목이 술 이름(없으면 "이름 미확인"), 뒤로 링크 `href="/"`.
    - `"알_수_없는_상태는_코드를_보이지_않는다"` — status `"WEIRD"` → "WEIRD" 텍스트 없음, "상태를 알 수 없어요." 보임.
    - `"불러오는_중에는_뼈대를_보인다"` — 첫 응답 전 `role="status"` 이름 "불러오는 중".
- [ ] **Step 2:** `pnpm test components/post-overview` → FAIL.
- [ ] **Step 3: 구현**
  - `page.tsx`: `AppNav`·`h1` 제거, `main`은 `mx-auto flex w-full max-w-sm flex-col`(edit 화면과 같은 모양). 상단 바는 `PostOverview`가 그린다(데이터가 있어야 제목을 알므로). 오류·불러오는 중일 때도 `TopBar title="후기" backHref="/"`.
  - `PhotoCarousel`: `role="region" aria-label="사진"`의 가로 스크롤 `flex snap-x snap-mandatory overflow-x-auto` + 스크롤바 숨김(`[scrollbar-width:none]`). 각 칸 `w-full shrink-0 snap-center aspect-[4/5]`, `img`는 `size-full object-cover`, src `${url}?v=${version}`. 현재 번호는 `onScroll`에서 `Math.round(scrollLeft / clientWidth)`로 state. 번호 배지 `absolute right-2 top-2 rounded-full bg-bg/80 px-2 text-xs text-text tabular-nums`(`aria-hidden`, alt에 번호가 있으므로).
  - `Review`: 사진 → `PhotoCarousel`(화면 가장자리까지, 좌우 여백 없이), 그 아래 `px-4` 영역에 술 이름 `font-serif text-2xl`, 별점 `text-accent`, 양조장 정보 `text-muted`, 캡션 `whitespace-pre-wrap text-text`.
  - 주 동작은 화면 아래 고정 막대: `sticky bottom-0 border-t border-line bg-bg px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]`. 검토 대기(웹)=`buttonClass("primary","w-full")` "게시하기", 실패·다시 시도 가능=“다시 시도”, 게시됨·permalink 있음="인스타그램에서 보기"(링크). 고정 막대가 없는 상태는 막대를 그리지 않는다.
  - "수정"은 `TopBar actions`에 `buttonClass("text")` 링크(편집 가능할 때만).
  - "게시물 취소"는 본문 끝의 `buttonClass("text", "text-danger")` 버튼. 확인창 `confirmLabel="게시물 취소" cancelLabel="돌아가기"`, `children`에 `<p className="text-sm text-muted">취소하면 되돌릴 수 없어요.</p>`. 게시 확인창 `confirmLabel="게시하기" cancelLabel="돌아가기"`.
  - 진행 중 점 `bg-accent`(`motion-safe:animate-pulse`), 실패 문구 `text-danger`, 오류 `text-danger`. `default` 분기 "상태를 알 수 없어요.".
  - 불러오는 중: 4:5 `Skeleton` 하나 + 줄 두 개, `role="status" aria-label="불러오는 중"`.
  - 확인·버전 충돌 처리(`pendingAction`·`run`·reset effect)는 그대로 둔다.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 게시물 개요 새 구성`

### Task 5: 양조장 목록

**Files:**
- Modify: `apps/web/components/breweries/BreweryList.tsx`, `apps/web/app/breweries/page.tsx`
- Create: `apps/web/components/breweries/BreweryList.test.tsx`

- [ ] **Step 1: 실패하는 테스트** (`BreweryList.test.tsx`, `useSearchParams`·`apiFetch` 목은 `PostList.test.tsx` 방식)
  - `"추가_링크가_제목_줄에_있다"` — 이름 "추가" 링크 `href="/breweries/new"`.
  - `"자동으로_찾은_양조장만_표시한다"` — `verified:false` 행에 "확인 필요", `verified:true` 행에는 표시 없음. "검증됨"·"미검증" 텍스트 없음.
  - `"검색_결과가_없으면_검색어와_추가_안내"` — `q="해창"`, 빈 목록 → "‘해창’에 맞는 양조장이 없어요." 와 이름 "새 양조장 추가" 링크.
  - `"검색어_없이_비면_추가_안내"` — 빈 목록 → "아직 양조장이 없어요."
- [ ] **Step 2:** `pnpm test components/breweries/BreweryList.test.tsx` → FAIL.
- [ ] **Step 3: 구현**
  - 제목 줄: `app/breweries/page.tsx`의 `h1` 옆 오른쪽에 `buttonClass("text")` 링크 "추가"(`flex items-center justify-between`). 넓은 "새 양조장" 띠는 지운다.
  - 검색: 입력 `placeholder="양조장 이름"`, 버튼 `buttonClass("secondary")`.
  - 행: 이름 `font-medium text-text`, 주소 `text-muted`(없으면 줄 생략), `verified:false`면 오른쪽에 `text-xs text-accent` "확인 필요". 행 `active:bg-line`.
  - 불러오는 중: `Skeleton` 행 4개(`role="status" aria-label="불러오는 중"`).
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 양조장 목록 새 구성`

### Task 6: 양조장 상세·추가

**Files:**
- Modify: `apps/web/app/breweries/[id]/page.tsx`, `apps/web/app/breweries/new/page.tsx`, `apps/web/components/breweries/BreweryDetail.tsx`, `apps/web/components/breweries/BreweryForm.tsx`, `apps/web/components/breweries/BreweryForm.test.tsx`

- [ ] **Step 1: 실패하는 테스트** (`BreweryForm.test.tsx`)
  - `"칸마다_맞는_키보드"` — "전화" 입력 `type="tel"`, "홈페이지" `type="url"`, "인스타그램" `placeholder="@ 없이 아이디만"`.
  - `"새로_만들면_상세_주소로_옮긴다"` — `onSaved` 없이, brewery 없이 저장 성공(id 7) → `router.replace("/breweries/7")` 1번. (목의 `useRouter`가 같은 `replace` 목을 돌려주도록 hoisted로 바꾼다.)
  - `"있는_양조장을_저장하면_머문다"` — brewery 있음 → `replace` 안 불림, "저장했어요. 다음 게시물부터 이 정보가 쓰여요." 보임.
  - 기존 테스트 중 성공 문구 "저장했습니다. 다음 게시물부터 이 정보가 쓰입니다."는 새 문구로 고친다.
- [ ] **Step 2:** `pnpm test components/breweries` → FAIL.
- [ ] **Step 3: 구현**
  - `BreweryForm`: 필드 정의에 `type`·`inputMode`·`autoComplete`·`placeholder`를 더한다(전화 `tel`/`tel`, 홈페이지 `url`/`url`, 나머지 text). 라벨 `text-sm text-muted`. 저장 버튼은 폼 끝 `sticky bottom-0 bg-inherit pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]` 영역의 `buttonClass("primary","w-full")`(시트 안에서도 같은 코드). 생성 모드(`brewery` 없음, `onSaved` 없음) 저장 성공이면 `router.replace(\`/breweries/${saved.id}\`)`.
  - `BreweryDetail`: `TopBar title={brewery.data.name} backHref="/breweries"` + 폼(`px-4 pt-4`). 불러오는 중·오류에도 `TopBar title="양조장" backHref="/breweries"`, 색은 토큰.
  - `breweries/[id]/page.tsx`: `AppNav`·`h1` 제거, `main`은 `mx-auto flex w-full max-w-sm flex-col`.
  - `breweries/new/page.tsx`: 같은 `main`에 `TopBar title="새 양조장" backHref="/breweries"` + `div.px-4.pt-4` 안의 `BreweryForm`.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS. `BrewerySheet.test.tsx`도 통과해야 한다(시트 저장 동작 그대로).
- [ ] **Step 5: 커밋** `Feat(web) : 양조장 상세·추가 새 구성`

### Task 7: 설정, 404·오류 화면, 옛 색 검사

**Files:**
- Modify: `apps/web/components/settings/InstagramTokenPanel.tsx`, `apps/web/components/settings/InstagramTokenPanel.test.tsx`, `apps/web/app/theme.test.ts`
- Create: `apps/web/app/not-found.tsx`, `apps/web/app/error.tsx`

- [ ] **Step 1: 실패하는 테스트**
  - `InstagramTokenPanel.test.tsx`: 성공 문구 → "인스타그램 토큰을 다시 불러왔어요.". 서버 오류 메시지 표시 테스트는 그대로(서버 문구는 웹이 바꾸지 않는다).
  - `theme.test.ts`에 `"화면 코드에 옛 색 클래스가 없다"` — `app/`·`components/` 아래 `.tsx`·`.ts`(테스트 파일 제외)를 재귀로 읽어 정규식 `/\b(?:bg|text|border|outline|ring|divide)-(?:neutral|red|green|white|black)\b|-(?:neutral|red|green)-\d{2,3}\b/`에 맞는 곳이 없다. 실패 메시지에 파일명을 넣는다. (`bg-black/60` 같은 확인창 배경막은 정규식의 `\b` 다음이 `/`이므로 걸린다 → `ConfirmDialog`·`BottomSheet` 배경막은 `backdrop:bg-bg/80`·`bg-bg/80`으로 바꾼다.)
- [ ] **Step 2:** `pnpm test app/theme.test.ts components/settings` → FAIL.
- [ ] **Step 3: 구현**
  - `InstagramTokenPanel`: 섹션을 `rounded-field border border-line bg-surface p-4`로, 제목 "인스타그램 연결", "마지막 반영" 줄 `text-muted`, 버튼 `buttonClass("secondary","w-full")` "토큰 다시 불러오기", 성공 `role="status" text-accent`, 오류 `text-danger`.
  - `not-found.tsx`: 가운데 정렬 아닌 왼쪽 정렬 `main`(`mx-auto max-w-sm px-4 py-16`), "찾는 화면이 없어요." + `buttonClass("secondary")` 링크 "기록으로 가기"(`/`).
  - `error.tsx`(`"use client"`, `{ error, reset }`): "화면을 그리지 못했어요." + `buttonClass("primary")` "다시 시도"(`reset()`) + `buttonClass("text")` 링크 "기록으로 가기".
  - 검사에 걸리는 남은 옛 색 클래스를 모두 토큰으로 바꾼다.
- [ ] **Step 4:** `pnpm lint && pnpm test` PASS. `API_INTERNAL_URL=http://localhost:8080 pnpm build` 성공.
- [ ] **Step 5: 커밋** `Feat(web) : 설정·오류 화면과 옛 색 정리`
