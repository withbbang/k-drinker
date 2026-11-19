# 디자인 기반과 수정·공유 화면 구현 계획 (3단계)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 웹에 디자인 B "옹기" 토큰·글꼴·공용 부품을 깔고, 수정 화면(`/posts/[id]/edit`)과 공유 화면(`/share`)을 흔히 쓰는 앱의 사용 방식으로 바꾼다.

**Architecture:** 토큰은 Tailwind v4 `@theme`(`app/globals.css`)에 두고, 글꼴은 `next/font`로 자체 호스팅한다. 공용 부품은 `components/ui/`에 두고 두 화면이 같이 쓴다. 되돌리기는 기존 `useUndoable`에 "글자 입력 묶기"를 더해 게시물의 모든 입력으로 넓힌다. 사진 끌기는 dnd-kit. API 변경은 없다.

**Tech Stack:** Next.js 16 / React 19 / Tailwind v4 / React Query / Vitest + Testing Library / `@dnd-kit/core`·`@dnd-kit/sortable`·`@dnd-kit/utilities` / `pretendard`(npm, 글꼴 파일).

**Spec:** `docs/superpowers/specs/2026-11-19-web-redesign-design.md` (3단계 절, 제약, 빌더 재량)

## Global Constraints

- 브랜치 `feat/design-foundation`(`dev`에서 딴다). 바뀌는 앱은 `apps/web`뿐이다. 커밋 전 `cd apps/web && pnpm lint && pnpm test` 통과.
- pnpm만 쓴다. 의존성 추가는 `pnpm add`(Task 1: `pretendard`, Task 4: `@dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities`). `cva`는 들이지 않는다(변형이 셋뿐이라 유틸 함수로 충분).
- 커밋 메시지는 `.gitmessage.txt`를 읽고 `Type(web) : 제목`(50자 이내), 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- `.claude/rules/web.md`: Tailwind만, JSX `style={}` 금지(dnd-kit transform만 예외, 쓰면 이유 주석), `div onClick` 금지, API 호출은 `lib/api.ts` 래퍼.
- 토큰 값(그대로 쓴다): `bg #1D1713`, `surface #29211B`, `line #3A2F27`, `text #EDE5D8`, `muted #A8998A`, `accent #D6B04C`, `on-accent #1D1713`, `danger #F08A7E`.
- 모든 글자·바탕 조합 WCAG AA(본문 4.5:1) 이상.
- 글꼴: 본문·UI Pretendard, 술 이름에만 고운바탕(Gowun Batang).
- 모서리: 입력칸·칩 10px, 썸네일 6px, 주 버튼 알약형. 그림자 대신 `line` 테두리.
- 움직임은 사용자 조작 반응(끌기·토스트·시트)만, 모두 `motion-safe:` 변형으로 건다(`prefers-reduced-motion`이면 꺼짐). 그라데이션·입장 애니메이션·카드 남발 금지.
- 화면에 개발용 이름("캡션 머리", "캡션 꼬리", "양조장 블록")을 쓰지 않는다.
- 사진 입력은 `accept="image/jpeg,image/png"`, 한도 10장 유지.
- 도메인 로직(이름표 채우기·조사·글자 수)은 웹에 두지 않는다. 글자 수·채운 글은 미리보기 API 값만 쓴다.
- 기존 테스트는 접근성 이름·문구로 요소를 찾는다. 사용 방식이 바뀐 곳(위로/아래로, 빼기 확인창, 캡션 세 칸, 하단 저장 버튼)의 테스트는 새 동작으로 다시 쓴다.
- 스타일을 다루는 태스크(1·2·4~8)의 구현자는 시작 전에 `frontend-design:frontend-design`과 `taste-skill:taste-skill` 스킬을 읽고 따른다(사용자 지정 스킬). 이 계획의 토큰·문구가 스킬의 일반 권고보다 우선한다.

## Review Focus

1. **터치 스크롤 중 사진 순서 바뀜**: 썸네일 줄 위에서 손가락을 대고 바로 움직이면 화면이 스크롤돼야 하고 순서는 그대로여야 한다 → Task 4에서 터치 센서 설정 상수(`delay 250ms`, `tolerance 5px`)를 테스트로 고정한다.
2. **글자 입력과 다른 변경이 섞인 되돌리기**: 메모를 친 뒤 별점을 바꾸고 ↶를 누르면 별점만, 한 번 더 누르면 메모가 통째로 돌아가야 한다 → Task 3 리듀서 테스트.
3. **토스트의 실행 취소가 엉뚱한 것을 되돌림**: 사진을 빼고 별점을 바꾼 뒤 토스트의 실행 취소를 누르면 별점이 되돌려진다 → 다른 변경이 생기면 토스트를 닫는다. Task 7 테스트.
4. **한글 입력이 여러 단계로 쪼개져 기록됨**: "안녕하세요"를 입력하고 ↶ 한 번이면 입력 전으로 돌아가야 한다 → Task 7 테스트.
5. **포커스 없이 이름표 넣기**: 편집칸을 한 번도 누르지 않고 이름표 버튼을 누르면 첫 칸 끝에 들어가고 그 칸에 커서가 간다 → Task 6 테스트.

---

### Task 1: 토큰·글꼴·테마 색

**Files:**
- Modify: `apps/web/app/globals.css`, `apps/web/app/layout.tsx`, `apps/web/app/manifest.ts`, `apps/web/package.json`
- Create: `apps/web/app/fonts.ts`, `apps/web/app/theme.test.ts`

**Interfaces:**
- Produces: Tailwind 유틸 `bg-bg` `bg-surface` `border-line` `text-text` `text-muted` `bg-accent` `text-accent` `text-on-accent` `text-danger` `font-sans`(Pretendard) `font-serif`(고운바탕) `rounded-field`(10px) `rounded-thumb`(6px). `app/fonts.ts`: `export const pretendard`, `export const gowunBatang` (각각 CSS 변수 `--font-pretendard`, `--font-gowun`).

- [ ] **Step 1: 실패하는 테스트** `app/theme.test.ts`
  - `globals.css`를 `fs`로 읽어 `@theme`의 `--color-*` 8개 값이 위 토큰과 같은지 본다.
  - 대비 검사: 테스트 안에 WCAG 상대 휘도 함수를 두고 `text/bg`, `text/surface`, `muted/bg`, `muted/surface`, `accent/bg`, `accent/surface`, `on-accent/accent`, `danger/bg`, `danger/surface`가 모두 `>= 4.5`.
  - `manifest()`의 `background_color`·`theme_color`가 `"#1D1713"`, `layout.tsx`의 `viewport.themeColor`가 `"#1D1713"`.
- [ ] **Step 2:** `pnpm test app/theme.test.ts` → FAIL.
- [ ] **Step 3: 구현**
  - `pnpm add pretendard`. `fonts.ts`: Pretendard는 `next/font/local`로 `node_modules/pretendard/dist/web/variable/woff2/PretendardVariable.woff2`(설치 후 실제 경로 확인), 고운바탕은 `next/font/google`의 `Gowun_Batang`(weight 400·700, `display: "swap"`; subsets 등 옵션은 `node_modules/next/dist/compiled/@next/font` 타입을 읽고 정한다).
  - `globals.css` `@theme`에 색 8개, `--font-sans: var(--font-pretendard), system-ui, sans-serif`, `--font-serif: var(--font-gowun), serif`, `--radius-field: 10px`, `--radius-thumb: 6px`. `html { color-scheme: dark; }`.
  - `layout.tsx`: `<html>`에 두 글꼴 변수 클래스, `<body className="bg-bg text-text font-sans antialiased">`, `themeColor: "#1D1713"`.
- [ ] **Step 4:** `pnpm test` 전체 PASS, `pnpm lint` PASS. 다른 화면은 4단계에서 입히므로 이 시점의 색 어긋남은 그대로 둔다(글자가 읽히는지만 확인).
- [ ] **Step 5: 커밋** `Feat(web) : 옹기 디자인 토큰과 글꼴`

### Task 2: 공용 부품

**Files:**
- Create: `apps/web/components/ui/button.ts`, `TopBar.tsx`, `AutoTextarea.tsx`, `Chip.tsx`, `Toast.tsx`, `BottomSheet.tsx`, `apps/web/hooks/use-toast.ts` + 각각의 `*.test.tsx`(button은 `button.test.ts`)
- Modify: `apps/web/components/ui/UndoRedoBar.tsx`, `ConfirmDialog.tsx`(재스타일만, 동작·props 그대로)

**Interfaces:**
- Produces:
  - `buttonClass(variant: "primary" | "secondary" | "text", extra?: string): string` — primary는 `bg-accent text-on-accent rounded-full`, secondary는 `border border-line bg-surface rounded-full`, text는 `text-accent`. 모두 `min-h-11`, `disabled:opacity-40`, `focus-visible:outline-2 outline-accent`.
  - `TopBar({ title, backHref, actions }: { title: string; backHref: string; actions?: ReactNode })` — `<header>` `sticky top-0 z-30 bg-bg border-b border-line`, 왼쪽 뒤로 링크(`aria-label="뒤로"`), 가운데 `<h1>{title}</h1>`, 오른쪽 `actions`.
  - `UndoRedoBar` props 그대로(`canUndo, canRedo, disabled?, onUndo, onRedo`), 글자 버튼 대신 아이콘 버튼 두 개(↶ ↷ SVG), 접근성 이름 "되돌리기"·"다시 하기" 그대로.
  - `AutoTextarea(props: ComponentProps<"textarea">)` — `field-sizing-content`로 늘어나고, `CSS.supports("field-sizing", "content")`가 거짓이면 `input` 때 ref로 높이를 `scrollHeight`에 맞춘다. 기본 `rows={3}`, `rounded-field border-line bg-surface`.
  - `Chip({ label, onActivate, onClear, disabled? }: { label: string; onActivate: () => void; onClear: () => void; disabled?: boolean })` — 본문 버튼(이름 `label`) + × 버튼(`aria-label={\`${label} 지우기\`}`).
  - `useToast(): { toast: ToastState | null; show(message: string, action?: { label: string; onAction: () => void }): void; dismiss(): void }`. `ToastState = { id: number; message: string; action?: {...} }`. 새 `show`는 이전 것을 바꾼다. 5000ms 뒤 저절로 닫힌다(`TOAST_MS = 5000` export).
  - `Toast({ toast, onDismiss }: { toast: ToastState | null; onDismiss: () => void })` — 화면 아래 고정, `role="status"`, 동작 버튼을 누르면 `onAction` 후 닫는다.
  - `BottomSheet({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode })` — body로 포탈한 네이티브 `<dialog>` 모달, 아래에서 올라옴(`motion-safe:`), Esc·배경 누르기로 `onClose`, 닫히면 연 버튼으로 포커스 복귀, 안쪽 `submit` 이벤트가 바깥으로 올라가지 않게 막는다(지금 `BrewerySheet`의 처리를 이리로 옮긴다).

- [ ] **Step 1: 실패하는 테스트**
  - `buttonClass("primary")`에 `bg-accent`·`rounded-full` 포함, `buttonClass("text", "ml-2")`에 `ml-2` 포함.
  - TopBar: `getByRole("link", { name: "뒤로" })`의 `href`가 `backHref`, `getByRole("heading", { name: title })`, actions 렌더.
  - Chip: 본문 클릭 → `onActivate` 1회, `"X 지우기"` 클릭 → `onClear` 1회.
  - useToast + Toast (fake timers): `show("사진을 뺐어요", { label: "실행 취소", onAction })` → `getByRole("status")`에 문구, "실행 취소" 클릭 → `onAction` 1회 후 사라짐. 다시 `show` 후 `TOAST_MS` 지나면 사라짐.
  - BottomSheet: 열리면 `getByRole("dialog", { name: title })`, Esc → `onClose`, 안쪽 폼 submit이 바깥 `<form onSubmit>`을 부르지 않음.
  - AutoTextarea: 여러 줄 입력 후 `value`에 `"\n"`이 그대로.
  - 기존 `ConfirmDialog.test.tsx`는 그대로 통과해야 한다.
- [ ] **Step 2:** `pnpm test components/ui hooks/use-toast` → FAIL.
- [ ] **Step 3:** 위 Interfaces대로 구현한다.
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 공용 화면 부품`

### Task 3: 글자 입력을 묶는 되돌리기

**Files:**
- Modify: `apps/web/hooks/use-undoable.ts`, `apps/web/hooks/use-undoable.test.ts`

**Interfaces:**
- Produces: `useUndoable<T>(initial: T, options?: { limit?: number; groupMs?: number })` (기본 50, 1000). 반환값에 기존 `state, set, undo, redo, canUndo, canRedo`에 더해 `setTyping(key: string, next: T | ((prev: T) => T)): void`, `endTyping(): void`. 기존 호출 `useUndoable(x)`은 그대로 동작한다(두 번째 인자를 숫자로 넘기는 호출부가 있으면 옵션 객체로 고친다).
- 리듀서: 상태에 `group: { key: string; at: number } | null`을 더한다. `set` 액션에 `group?: { key: string; at: number; windowMs: number }`. 규칙:
  - `group`이 있고 `state.group?.key === group.key`이고 `group.at - state.group.at <= windowMs`이면 `past`에 넣지 않고 `present`만 바꾸고 `future`를 비우며 `state.group.at`을 갱신한다.
  - 그 밖의 `set`은 지금처럼 기록하고, `group`이 있으면 `state.group = { key, at }`, 없으면 `null`.
  - `undo`·`redo`·`endTyping`(`{ type: "endGroup" }`)은 `group`을 `null`로.
  - 같은 값이면 지금처럼 아무것도 하지 않는다.
  - `setTyping`은 `Date.now()`를 `at`으로 넘긴다.

- [ ] **Step 1: 실패하는 테스트** (`undoableReducer`에 `at`을 직접 넘긴다)
  - `메모를_1초_안에_이어_치면_한_번으로_기록된다`: key "memo"로 at 0, 500, 900 → `past.length === 1`, undo 한 번이면 처음 값.
  - `1초_넘게_멈췄다_치면_따로_기록된다`: at 0, 1500 → `past.length === 2`.
  - `다른_칸으로_옮기면_따로_기록된다`: "memo" at 0 → "caption" at 100 → `past.length === 2`.
  - `endGroup_뒤에는_따로_기록된다`.
  - Review Focus 2 — `메모를_치고_별점을_바꾸면_되돌리기가_하나씩_돌아간다`: memo at 0, 300 → 그룹 없는 set(rating) → undo → rating만 원래, memo는 친 값 → undo → memo 처음 값.
  - 기존 테스트 모두 통과.
- [ ] **Step 2:** `pnpm test hooks/use-undoable` → FAIL.
- [ ] **Step 3:** 위 규칙대로 구현.
- [ ] **Step 4:** `pnpm test` 전체 PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 글자 입력을 묶는 되돌리기`

### Task 4: 사진 썸네일 줄 (dnd-kit)

**Files:**
- Create: `apps/web/components/ui/PhotoStrip.tsx`, `PhotoStrip.test.tsx`, `apps/web/components/ui/photo-strip.ts`, `photo-strip.test.ts`, `apps/web/components/post-edit/PhotoViewerSheet.tsx`, `PhotoViewerSheet.test.tsx`
- Modify: `apps/web/package.json`

**Interfaces:**
- Consumes: `BottomSheet`(Task 2), `AddPhotoTile`(기존, 그대로).
- Produces:
  - `photo-strip.ts`: `export const TOUCH_ACTIVATION = { delay: 250, tolerance: 5 } as const`, `export const MOUSE_ACTIVATION = { distance: 5 } as const`, `moveByKey<T>(items: T[], getKey: (item: T) => string, fromKey: string, toKey: string): T[]`(같은 키거나 없는 키면 같은 배열 그대로).
  - `PhotoStrip<T>(props)`:
    ```ts
    type PhotoStripProps<T> = {
      items: T[];
      getKey: (item: T) => string;
      renderImage: (item: T, className: string) => ReactNode;
      isPadded?: (item: T) => boolean;      // 주면 여백 상태를 썸네일에 보인다
      disabled?: boolean;
      max: number;                          // 10
      onReorder: (next: T[]) => void;
      onRemove: (key: string) => void;      // 확인창 없음. 토스트는 부모가 띄운다
      onOpen?: (key: string) => void;       // 주면 썸네일을 누를 때 부른다
      onBusyChange?: (busy: boolean) => void; // 끌기 시작 true, 끝·취소 false
      onAdd?: (files: File[]) => void;
      onOverflow?: (skipped: number) => void;
    };
    ```
    가로 스크롤 줄, 썸네일 4:5(`aspect-[4/5]`) `rounded-thumb`. 첫 장 위에 "대표" 표시. 썸네일 버튼 이름 `${i + 1}번째 사진` (첫 장은 `1번째 사진, 대표`). 모서리 × 배지 `aria-label={\`${i + 1}번째 사진 빼기\`}`, 한 장만 남으면 렌더하지 않는다. 여백 상태면 `object-contain bg-surface`, 아니면 `object-cover`, 그리고 화면 읽기용 "여백 있음". 센서: `TouchSensor`(TOUCH_ACTIVATION), `MouseSensor`(MOUSE_ACTIVATION), `KeyboardSensor`(`sortableKeyboardCoordinates`), 전략 `horizontalListSortingStrategy`. 끌기 끝나면 `onReorder(moveByKey(...))`. 끝에 `items.length < max`이고 `onAdd`가 있으면 `AddPhotoTile`. 썸네일 글자 버튼으로 사진을 덮지 않는다.
  - `PhotoViewerSheet({ title, image, padded, onTogglePad, onClose, disabled? }: { title: string; image: ReactNode; padded: boolean; onTogglePad: () => void; onClose: () => void; disabled?: boolean })` — BottomSheet 안에 큰 사진과 버튼 하나("여백 넣기" / 여백이면 "여백 빼기").

- [ ] **Step 1: 실패하는 테스트**
  - `moveByKey`: `["a","b","c"]`에서 a→c → `["b","c","a"]`, c→a → `["c","a","b"]`, 없는 키 → 원래 배열.
  - Review Focus 1: `TOUCH_ACTIVATION`이 `{ delay: 250, tolerance: 5 }`.
  - PhotoStrip: 첫 썸네일 이름에 "대표"; "2번째 사진 빼기" 클릭 → `onRemove("<2번째 키>")`; 한 장일 때 "빼기" 버튼 없음; 썸네일 클릭 → `onOpen(key)`; `max`장이면 추가 타일 없음; `disabled`면 빼기·추가 막힘; 여백 항목은 "여백 있음" 텍스트.
  - PhotoViewerSheet: `padded=false`면 "여백 넣기" 클릭 → `onTogglePad` 1회; `padded=true`면 "여백 빼기".
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** `pnpm add @dnd-kit/core @dnd-kit/sortable @dnd-kit/utilities` 후 구현. 정렬 항목의 `transform`·`transition`은 `style`로만 줄 수 있으므로 그 한 곳에 이유 주석을 단다. 끄는 중 표시는 `motion-safe:` 전환.
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 끌어서 옮기는 사진 줄`

### Task 5: 칩으로 바뀌는 술·양조장 검색

**Files:**
- Modify: `apps/web/components/post-edit/SearchCombobox.tsx`, `BreweryPicker.tsx`, `DrinkPicker.tsx` + 세 테스트 파일

**Interfaces:**
- Consumes: `Chip`(Task 2).
- Produces: `SearchCombobox` props에 `selectedLabel: string | null`, `onClear: () => void`, `onBlurField?: () => void` 추가, `inputClassName` 제거(토큰 스타일을 안에서 정한다). 동작:
  - `selectedLabel`이 있고 편집 중이 아니면 검색칸 자리에 `Chip`.
  - 칩 본문 → 검색칸으로 바뀌고 포커스. 고르지 않고 포커스가 나가면 다시 칩(선택 유지).
  - 칩 × → `onClear()` 후 검색칸으로 바뀌고 포커스.
  - 결과 목록은 입력칸 아래 떠 있다(`relative` 래퍼 + `absolute z-20 w-full`, `bg-surface border-line rounded-field`), 폼을 밀지 않는다. 바깥 `pointerdown`이나 Esc면 닫힌다.
  - 기존 키보드·IME(`justComposed`)·combobox ARIA는 그대로.
- `BreweryPicker`: "선택한 양조장: X" 문장 삭제. × = `picked=null`(술의 기본 양조장). `DrinkPicker`: × = 선택 없음, 새 이름 등록 항목도 같은 떠 있는 목록 모양. 바깥에서 쓰는 props(`PostEditForm`이 넘기는 것)는 이 태스크에서 바꾸지 않는다.

- [ ] **Step 1: 실패하는 테스트**
  - SearchCombobox: `selectedLabel="서울장수"` → 버튼 "서울장수"와 "서울장수 지우기"가 보이고 검색 입력은 없음. 칩 클릭 → `combobox`가 포커스를 가짐. × 클릭 → `onClear` 1회, `combobox` 포커스. 목록이 열린 상태에서 Esc → `listbox` 사라짐. 바깥 버튼을 `pointerdown` → `listbox` 사라짐.
  - BreweryPicker: "선택한 양조장" 텍스트가 없음, 고른 뒤 칩에 양조장 이름.
  - 기존 키보드·IME 테스트 유지.
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 칩으로 보이는 술·양조장 선택`

### Task 6: 인스타그램 글 편집칸

**Files:**
- Create: `apps/web/components/post-edit/CaptionEditor.tsx`, `CaptionEditor.test.tsx`

**Interfaces:**
- Consumes: `AutoTextarea`(Task 2).
- Produces:
  ```ts
  type CaptionEditorProps = {
    head: string;
    tail: string;
    onHeadChange: (value: string) => void;
    onTailChange: (value: string) => void;
    onFieldBlur: () => void;            // 부모가 endTyping을 부른다
    templated: boolean;                 // 이름표 캡션인지 (post.captionTemplated)
    preview: { caption: string | null; length: number; fetching: boolean; error: boolean };
    disabled?: boolean;
  };
  export const PLACEHOLDER_BUTTONS = ["{술이름}", "{종류}", "{도수}", "{용량}", "{별점}", "{양조장}"] as const;
  export const MAX_CAPTION_LENGTH = 2200;
  ```
  - 제목 "인스타그램 글", 탭 두 개(`role="tablist"`, "편집"·"미리보기").
  - 편집 탭: 한 덩어리로 보이는 테두리 상자 안에 위 칸(head) · 고정 한 줄 "양조장 정보는 자동으로 채워져요"(편집 불가, `text-muted`) · 아래 칸(tail). 두 칸 접근성 이름은 "인스타그램 글 앞부분"·"인스타그램 글 뒷부분"(화면에 보이는 라벨은 없다). 마커는 보이지도, 지울 수도 없다(두 칸이 마커 바깥이므로).
  - `templated`일 때만 아래에 이름표 버튼 줄: 보이는 글자는 중괄호 뺀 이름, `aria-label={\`${name} 넣기\`}`. 누르면 마지막으로 포커스된 칸(없으면 head)의 커서 자리에 넣고, 그 칸에 포커스·커서를 넣은 글 뒤로. 이름표 캡션이 아닌 글에서는 버튼을 숨긴다(넣는 순간 이름표 캡션으로 바뀌어 별점 갱신이 멈추는 문제를 피한다).
  - 미리보기 탭: `preview.caption`을 `whitespace-pre-wrap`로, `fetching`이면 `opacity-50`, `error`면 `role="alert"` "미리보기를 불러오지 못했어요".
  - 글자 수 줄은 탭과 상관없이 늘 보인다: `{length.toLocaleString("ko-KR")}자 / 2,200자`, 넘으면 `text-danger`와 `role="alert"` "2,200자를 넘었어요".

- [ ] **Step 1: 실패하는 테스트**
  - 처음에는 편집 탭, "미리보기" 탭 클릭 → `preview.caption`의 줄바꿈이 그대로(`toHaveClass("whitespace-pre-wrap")`).
  - 화면에 "캡션 머리"·"캡션 꼬리"·"양조장 블록" 텍스트가 없음. "양조장 정보는 자동으로 채워져요"가 있음.
  - 앞부분 칸 `"좋은 술"`에서 커서를 3에 두고 "술이름 넣기" → `onHeadChange("좋은 {술이름}술")`.
  - Review Focus 5: 아무 칸도 누르지 않고 "도수 넣기" → `onHeadChange(head + "{도수}")`, 앞부분 칸에 포커스.
  - 뒷부분 칸에 포커스 후 "별점 넣기" → `onTailChange` 호출.
  - `templated=false`면 "넣기" 버튼이 하나도 없음.
  - `length=2201` → alert "2,200자를 넘었어요", `error=true` → alert "미리보기를 불러오지 못했어요".
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 한 덩어리로 보이는 인스타그램 글 편집칸`

### Task 7: 수정 화면 조립

**Files:**
- Modify: `apps/web/components/post-edit/PostEditForm.tsx`, `PostEditForm.test.tsx`, `apps/web/app/posts/[id]/edit/page.tsx`, `apps/web/components/post-edit/BrewerySheet.tsx`, `BrewerySheet.test.tsx`

**Interfaces:**
- Consumes: Task 2 전부, `useUndoable`의 `setTyping/endTyping`(Task 3), `PhotoStrip`·`PhotoViewerSheet`(Task 4), 새 `SearchCombobox` props(Task 5), `CaptionEditor`(Task 6).
- 되돌리기 상태를 넓힌다:
  ```ts
  type EditState = {
    photos: PhotoItem[];
    rating: number | null;
    drink: DrinkChoice;
    picked: BreweryResponse | null;
    memo: string;
    captionHead: string;
    captionTail: string;
  };
  ```
  글자 칸(memo·captionHead·captionTail·새 술 이름)은 `setTyping("<칸 이름>", …)`, 칸을 떠나면 `endTyping()`. 그 밖의 변경은 `set`. 양조장 시트에서 고친 양조장 정보는 기록에 넣지 않는다(지금처럼 서버 저장).
- 화면 구성(스펙 3단계 그림 순서): `TopBar title="수정" backHref={\`/posts/${id}\`}` + actions `UndoRedoBar` · `<button type="submit" form={formId}>저장</button>`(`buttonClass("primary")`) → 술 이름 `<h2 className="font-serif">` → `PhotoStrip` → 술 → 양조장 + "양조장 정보 고치기"(시트) → 별점 → 메모(`AutoTextarea`, 2,000자 한도 유지) → `CaptionEditor` → 알림 → `Toast`. 폼 맨 아래 저장 버튼과 `page.tsx`의 `<h1>후기 수정</h1>`은 없앤다(제목은 TopBar가 맡는다).
- 저장 중·사진 끌기 중에는 저장·되돌리기·다시 하기를 막는다(기존 잠금).
- 사진 빼기: `set`으로 빼고 `show("사진을 뺐어요", { label: "실행 취소", onAction: undo })`. 이 뒤 어떤 변경(`set`·`setTyping`·undo·redo)이라도 생기면 토스트를 닫는다.
- 썸네일을 누르면 `PhotoViewerSheet`로 여백을 바꾼다(바꾸기는 `set`).
- `BrewerySheet`는 `BottomSheet` 위에 다시 짠다(제목 "양조장 정보 고치기", 동작 그대로).

- [ ] **Step 1: 테스트를 새 동작으로 다시 쓴다** (`PostEditForm.test.tsx`)
  - 위로/아래로 버튼·빼기 확인창·캡션 세 칸 라벨·하단 저장 버튼을 찾던 테스트는 지우거나 새 동작으로 바꾼다.
  - `상단_저장_버튼으로_저장한다`: 머리글 안의 "저장" 클릭 → update 요청 1회. 폼 안에 다른 "저장" 버튼 없음.
  - `사진을_빼면_토스트가_뜨고_실행_취소로_돌아온다`: "2번째 사진 빼기" → status "사진을 뺐어요" → "실행 취소" → 사진 수가 원래대로.
  - Review Focus 3 — `사진을_뺀_뒤_다른_것을_바꾸면_토스트가_닫힌다`: 빼기 → 별점 변경 → `queryByRole("status")` 없음.
  - Review Focus 4 — `한글_입력은_되돌리기_한_번에_돌아간다`: 메모에 `user.type("안녕하세요")` → "되돌리기" 한 번 → 메모가 원래 값.
  - `메모의_줄바꿈이_그대로_저장된다`: `"첫 줄{Enter}둘째 줄"` → 요청 body `memo === "첫 줄\n둘째 줄"`.
  - `술_선택도_되돌린다`: 다른 술 고르기 → 되돌리기 → 칩이 원래 술 이름.
  - `양조장_칩_x만_누르고_저장하면_술의_기본_양조장`: × → 저장 → 요청 body에 `breweryId`가 지금 `picked=null`일 때 보내는 값과 같다.
  - `썸네일을_눌러_여백을_넣는다`: 첫 썸네일 → "여백 넣기" → 저장 body의 첫 사진 `fitMode === "PAD"`.
  - 기존 미리보기·저장 실패·버전 처리 테스트는 새 구조에서 그대로 통과.
- [ ] **Step 2:** 새 테스트 FAIL 확인.
- [ ] **Step 3:** 구현. `BrewerySheet.test.tsx`는 그대로 통과해야 한다(이름 기준 조회).
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS.
- [ ] **Step 5: 커밋** `Feat(web) : 수정 화면 새 구성과 되돌리기 확대`

### Task 8: 공유 화면

**Files:**
- Modify: `apps/web/components/share/ShareForm.tsx`, `ShareForm.test.tsx`, `apps/web/app/share/page.tsx`(제목이 있으면 TopBar로 옮긴다)
- Delete: `apps/web/components/post-edit/PhotoOrderList.tsx`, `PhotoOrderList.test.tsx`(이제 쓰는 곳이 없다)

**Interfaces:**
- Consumes: Task 2, 3, 4.
- `EditState = { photos: SharedPhoto[]; rating: number | null; memo: string }`, 메모는 `setTyping("memo")`/`endTyping`.
- 구성: `TopBar title="새 기록" backHref="/"` + actions `UndoRedoBar` · "보내기"(`buttonClass("primary")`, 지금 [보내기]가 여는 확인창을 그대로 연다) → `PhotoStrip`(`onOpen`·`isPadded` 없이) → 별점 → 메모 `AutoTextarea` → `Toast`. 사진 빼기는 수정 화면과 같은 토스트·닫힘 규칙. 보내기 확인창의 메모 미리보기는 `whitespace-pre-wrap`.

- [ ] **Step 1: 테스트를 새 동작으로 다시 쓴다**
  - `사진을_빼면_토스트가_뜨고_실행_취소로_돌아온다`(수정 화면과 같은 단정).
  - `보내기_확인창의_메모가_줄바꿈을_지킨다`: 메모 `"a{Enter}b"` → 보내기 → 확인창 안 메모 요소가 `whitespace-pre-wrap`이고 텍스트가 `"a\nb"`.
  - `메모도_되돌린다`: 메모 입력 → 되돌리기 → 빈 메모.
  - 위로/아래로·빼기 확인창 테스트는 지운다.
- [ ] **Step 2:** FAIL 확인.
- [ ] **Step 3:** 구현하고 `PhotoOrderList` 두 파일을 지운다(`grep -r PhotoOrderList apps/web`가 비어야 한다).
- [ ] **Step 4:** `pnpm test` 전체·`pnpm lint` PASS, `API_INTERNAL_URL=http://localhost:8080 pnpm build` 성공.
- [ ] **Step 5: 커밋** `Feat(web) : 공유 화면 새 구성`
