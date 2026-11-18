# 사진 편집 보강·되돌리기 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공유·수정 화면에서 사진 빼기 확인·사진 추가·되돌리기/다시 하기를 넣고, 모든 확인창을 바깥 클릭으로 닫히는 공용 모달로 바꾼다.

**Architecture:** API는 `PUT /api/v1/posts/{id}`를 multipart(`request` JSON + `newPhotos` 파일)로 바꾸고, 새 파일은 공유 접수와 같은 스테이징 부품(`PhotoStager`)으로 처리한다. 웹은 사진 목록과 별점을 `useUndoable` 스냅숏 기록으로 관리하고, 새 사진은 브라우저 `File`로 들고 있다가 [저장] 때 한 번에 보낸다.

**Tech Stack:** Spring Boot / JPA / JUnit5 (apps/api), Next.js / React / TanStack Query / Vitest + Testing Library (apps/web)

**Spec:** `docs/superpowers/specs/2026-11-18-photo-edit-undo-design.md`

## Global Constraints

- 최종 사진 수 1~10장. 어기면 `POST_PHOTO_COUNT`(400). 새 에러 코드는 만들지 않는다(`api-conventions`).
- 새 파일의 형식·디코딩·용량 오류는 공유 접수와 같은 코드(`POST_PHOTO_TOO_LARGE`, `COMMON_VALIDATION_FAILED` 등). 업로드 한도(파일 20MB, 요청 210MB)는 그대로.
- 저장 실패 시 DB는 바뀌지 않고, `original/`·`rendered/`·`tmp/`에 새 파일이 남지 않는다. 일부 사진만 올라간 상태가 없다.
- 원본 경로는 기존 사진과 겹치지 않아야 한다.
- 새 사진은 초점 없이(`focus=null`) 요청의 fitMode로 렌더. Claude 재분석·캡션 재생성 없음, `decideCaption` 규칙 그대로.
- 버전 검사·`moveToWeb`·`PostEditedEvent`·수정 가능 상태 규칙은 지금 그대로.
- JSON PUT 호출은 남기지 않는다.
- 되돌리기 기록 대상: 사진 목록(순서·추가·빼기·여백)과 별점만. 메모·캡션 등 글자는 제외. 한 조작 = 한 칸, 새 조작 시 다시 하기 기록 삭제, 한도 50칸.
- 버튼 문구: [되돌리기] [다시 하기]. 할 게 없거나 사진 애니메이션 중이면 비활성.
- 확인 모달 문구: 사진 빼기/삭제 "이 사진을 뺄까요?" [빼기] [취소] / 로그아웃 "로그아웃할까요?" [로그아웃] [취소] / 개요 "인스타그램에 게시할까요?", "이 게시물을 취소할까요?"(기존 그대로).
- 모든 확인 모달: 배경 클릭 = 취소, Esc도 취소. 진행 중(pending)이면 둘 다 닫지 않는다.
- 공유 화면 [빼기]: 빨간(Tailwind red 계열) 동그란 버튼, 사진 안 오른쪽 위, 44px 이상, `aria-label="사진 N 빼기"`.
- [+] 타일: 목록 끝, `accept="image/*"` `multiple`. 10장이면 숨김. 넘친 만큼은 넣지 않고 안내: `사진은 10장까지 넣을 수 있어 {N}장은 넣지 않았습니다.`
- 커밋: `.gitmessage.txt` 형식 `Type(scope) : 제목`(50자 이내), 본문 줄 72자 이내, 꼬리말 두 줄(`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`, `Claude-Session: https://claude.ai/code/session_0187DRV3v1vLhtCJd2Dd33oe`). 커밋 전 바뀐 앱의 lint·test 통과.
- API 테스트는 context-mode 훅 때문에 Bash가 아닌 `ctx_execute`(shell)로 `./gradlew test`를 돌리고 `build/test-results/test/*.xml`로 센다. 웹은 `pnpm lint && pnpm test`(apps/web).

## Review Focus

1. 기존 사진 순서를 바꾸면서 앞쪽에 새 사진을 넣는 저장 → 기존 원본 파일이 덮이지 않고 새 원본 경로가 따로 생겨야 한다 (Task 1·2 테스트).
2. 새 사진을 고른 뒤 409로 저장 실패 → 서버에는 새 파일이 하나도 남지 않고, 웹은 새 사진·되돌리기 기록을 그대로 둔다 (Task 2·6 테스트).
3. `newIndex`와 파일 수가 안 맞음(참조 안 된 파일, 중복 newIndex, 범위 밖) → 400 `COMMON_VALIDATION_FAILED`, 파일 남지 않음 (Task 2 테스트).
4. 확인 모달이 진행 중일 때 배경 클릭·Esc → 닫히지 않음 (Task 3 테스트).
5. 9장에서 3장을 고름 → 1장만 들어가고 "2장은 넣지 않았습니다" 안내, 10장이 되면 [+] 숨김 (Task 4·5 테스트).

---

### Task 1: 스테이징 부품 분리와 고유한 원본 경로 (API)

**Files:**
- Create: `apps/api/src/main/java/com/kdrinker/post/service/PhotoStager.java`
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostIntakeService.java` (stage/Staged/rollback 정리를 `PhotoStager`로 옮김)
- Modify: `apps/api/src/main/java/com/kdrinker/media/PhotoStorage.java` (`originalPath(Long, String, String)` 추가)
- Test: `apps/api/src/test/java/com/kdrinker/media/PhotoStorageTest.java`, `apps/api/src/test/java/com/kdrinker/post/service/PhotoStagerTest.java`, 기존 `PostIntakeServiceTest`는 그대로 통과

**Interfaces:**
- Produces:
  - `PhotoStorage.originalPath(Long postId, String name, String format) -> String` = `"original/" + postId + "/" + name + "." + format`. `name`은 `TOKEN_PATTERN`이어야 하고 format은 jpg/png만(아니면 `IllegalArgumentException`).
  - `@Component class PhotoStager` (package `com.kdrinker.post.service`, public):
    - `String newStagingId()` — `Tokens.random(secureRandom)`
    - `Staged stage(String stagingId, int index, PhotoInput input, FitMode fitMode)` — 지금 `PostIntakeService.stage`와 같은 검사·예외(`POST_PHOTO_TOO_LARGE`, 읽기 실패 `COMMON_VALIDATION_FAILED`), 렌더는 `photoTransformer.render(decoded, null, fitMode)`.
    - `PostPhoto promote(Post post, int position, Staged staged, List<String> written)` — 원본을 `PhotoStorage.originalPath(post.getId(), Tokens.random(...), format)`(고유 이름)로, 렌더본을 `renderedPath(staged.token())`로 옮기고 두 경로를 `written`에 더한 뒤 `PostPhoto.of(post, position, originalPath, token, fitMode)` + `attachRendered`를 돌려준다(저장은 호출 쪽).
    - `void registerRollbackCleanup(List<String> written)` — 지금 PostIntakeService의 것 그대로.
    - `void deleteStaging(String stagingId)` — `photoStorage.deleteStagingQuietly`.
    - `public record Staged(String format, String token, FitMode fitMode, String originalStaged, String renderedStaged)`
- 접수 쪽 원본 경로도 고유 이름으로 바뀐다(기존 저장된 행의 경로는 그대로 유효; 마이그레이션 없음).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `PhotoStorageTest.원본_경로는_이름으로_만든다`: `originalPath(7L, token43, "jpg")` == `"original/7/" + token43 + ".jpg"`; 잘못된 이름(`"../x"`)·형식(`"gif"`)은 `IllegalArgumentException`.
  - `PhotoStagerTest.같은_게시물의_두_사진은_원본_경로가_다르다`: 임시 MEDIA_DIR로 같은 post에 같은 position 0으로 두 번 `promote` → 두 `getOriginalPath()`가 다르고 두 파일 모두 존재.
  - `PhotoStagerTest.PAD로_스테이징하면_PAD로_렌더한다`: transformer mock이 `render(any, isNull(), eq(FitMode.PAD))`로 불린다.
- [ ] **Step 2: 실패 확인** — `ctx_execute`: `cd apps/api && ./gradlew test --tests '*PhotoStorageTest' --tests '*PhotoStagerTest'` → 컴파일 실패/FAIL.
- [ ] **Step 3: 구현** — 위 시그니처대로. `PostIntakeService.receiveInputs`는 `PhotoStager`를 써서 동작이 같게(FitMode.CROP) 바꾼다.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 통과(xml로 실패 0 확인).
- [ ] **Step 5: 커밋** — `Refactor(api) : 사진 스테이징 분리와 고유한 원본 경로`

### Task 2: 수정 저장 multipart와 새 사진 추가 (API)

**Files:**
- Modify: `apps/api/src/main/java/com/kdrinker/post/dto/PostUpdateRequest.java` (`PhotoEdit`)
- Modify: `apps/api/src/main/java/com/kdrinker/post/service/PostEditService.java`
- Modify: `apps/api/src/main/java/com/kdrinker/post/controller/PostController.java`
- Modify(필요 시): `apps/api/src/main/java/com/kdrinker/common/GlobalExceptionHandler.java` — `MissingServletRequestPartException` → `COMMON_VALIDATION_FAILED`(기존 코드)
- Test: `PostEditServiceTest`, `PostEditIntegrationTest`, `PostControllerTest`

**Interfaces:**
- Consumes: Task 1의 `PhotoStager`, `Staged`, `PhotoInput`.
- Produces:
  - `record PhotoEdit(Long id, Integer newIndex, @NotNull FitMode fitMode)` — id·newIndex 중 정확히 하나.
  - `PostEditService.update(Long id, PostUpdateRequest req, List<PhotoInput> newPhotos) -> PostDetailResponse` (기존 2인자 메서드는 지운다).
  - `PUT /api/v1/posts/{id}` `consumes=multipart/form-data`: `@Valid @RequestPart("request") PostUpdateRequest`, `@RequestPart(name="newPhotos", required=false) List<MultipartFile>`. 컨트롤러의 `toInput`을 재사용.
- 사진 검사(snapshot 단계, 파일 읽기 전): 각 항목은 id·newIndex 중 정확히 하나, id는 이 게시물 사진이고 중복 없음, newIndex는 `0..newPhotos.size()-1`을 정확히 한 번씩(참조 안 된 파일·중복·범위 밖이면 `COMMON_VALIDATION_FAILED`), 항목 수가 1~10이 아니면 `POST_PHOTO_COUNT`.
- 처리 순서: snapshot(검증) → 트랜잭션 밖에서 새 파일 스테이징(각 항목의 fitMode로) → `decideCaption` → 쓰기 트랜잭션(save + 새 사진 `promote`·`postPhotoRepository.save`, 기존 사진 순서/여백/삭제, 전이) → `finally`에서 스테이징 디렉터리 삭제. 트랜잭션 안의 promote는 `registerRollbackCleanup`으로 롤백 시 지우고, 예외 시 `written`도 지운다(접수와 같은 모양).
  - Ruling: 스펙의 "커밋 뒤 promote" 대신 접수와 같은 "트랜잭션 안 promote + 롤백 정리"를 쓴다 — 스펙이 "공유 접수와 같은 모양"이라 했고, 커밋 뒤 옮기면 상세 응답이 없는 파일을 가리키는 틈이 생긴다. 실패 시 파일·DB가 남지 않는다는 목표는 같다.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `PostEditIntegrationTest.새_사진을_앞에_넣고_순서를_바꾸면_기존_원본이_그대로다`: 기존 사진 A(0)·B(1) 파일을 써 두고, `photos=[{newIndex:0,CROP},{id:B},{id:A}]` + 유효 JPEG 1장으로 저장 → 행 3개, position 0이 새 사진, A·B의 `originalPath` 파일 내용이 저장 전과 같고 새 원본 경로는 A·B와 다르며 존재, `tmp/` 비어 있음, 채널 WEB.
  - `PostEditIntegrationTest.저장이_롤백되면_새_사진_파일이_남지_않는다`: 전이 충돌(버전 불일치를 저장 직전에 만듦—기존 `롤백되면_파일이_그대로다` 방식 재사용)로 실패 → 사진 행 수 그대로, `original/{id}/`에 새 파일 없음, `rendered/` 파일 수 그대로, `tmp/` 비어 있음.
  - `PostEditServiceTest`: `newIndex_중복이면_검증_실패`, `참조하지_않은_새_파일이_있으면_검증_실패`, `id와_newIndex를_같이_주면_검증_실패`, `합쳐_11장이면_POST_PHOTO_COUNT` — 모두 `stage`가 한 번도 불리지 않음(`verify(stager, never()).stage(...)`).
  - `PostControllerTest.수정_저장은_multipart로_받는다`: `multipart(PUT, "/api/v1/posts/1")`에 `request` 파트(`application/json`)와 `newPhotos` 파일 1개 → 200, `postEditService.update(eq(1L), any(), argThat(list -> list.size()==1))`.
  - `PostControllerTest.request_파트가_없으면_400`: → 400, `error.code == "COMMON_VALIDATION_FAILED"`.
  - 기존 JSON PUT 테스트(`PostControllerTest:178` 근처)는 multipart로 바꾼다.
- [ ] **Step 2: 실패 확인** — `./gradlew test --tests '*PostEdit*' --tests '*PostControllerTest'` → FAIL.
- [ ] **Step 3: 구현** — 위 Interfaces대로. 기존 사진의 여백 재렌더(`photoService.rerender`)는 기존 사진에만.
- [ ] **Step 4: 통과 확인** — `./gradlew test` 전체 통과.
- [ ] **Step 5: 커밋** — `Feat(api) : 수정 저장에서 새 사진 추가`

### Task 3: 공용 확인 모달과 배경 클릭 닫기 (웹)

**Files:**
- Create: `apps/web/components/ui/ConfirmDialog.tsx`, `apps/web/components/ui/ConfirmDialog.test.tsx`
- Modify: `apps/web/components/share/ShareForm.tsx` (보내기 확인을 공용 모달로)
- Modify: `apps/web/components/layout/AppNav.tsx` (로그아웃 확인)
- Modify: `apps/web/components/post-overview/PostOverview.tsx` (`window.confirm` 제거)
- Test: `ShareForm.test.tsx`, `PostOverview.test.tsx`, `apps/web/components/layout/AppNav.test.tsx`(새로)

**Interfaces:**
- Produces: `ConfirmDialog(props: { title: string; confirmLabel: string; cancelLabel?: string /* 기본 "취소" */; pending?: boolean; children?: ReactNode; onConfirm: () => void; onClose: () => void })` — 네이티브 `<dialog>`를 마운트 시 `showModal()`. `onCancel`(Esc)은 `preventDefault` 후 `!pending`이면 `onClose`. `<dialog>` 자체를 누른 클릭(`event.target === event.currentTarget`, 즉 배경)이면 `!pending`일 때 `onClose`. 내용은 안쪽 래퍼 요소에 패딩을 둬서 내용 클릭이 배경으로 잡히지 않게 한다. 확인 버튼은 `onConfirm`, 두 버튼 모두 pending이면 비활성. `aria-labelledby`로 제목 연결.
- ShareForm 보내기 모달: `title="이대로 보낼까요?"`, `confirmLabel="보내기"`, `cancelLabel="돌아가기"`, 지금의 요약 `<dl>`을 children으로.
- PostOverview: `run`은 메시지가 있으면 `pendingAction` 상태에 넣고 모달에서 확인 시 실행. confirm은 `confirmLabel="게시"`가 아닌 기존 버튼 문구를 따른다: 게시 확인 모달 `[확인] [취소]`, 게시물 취소 모달 `[게시물 취소] [돌아가기]`. (Ruling: 스펙은 제목 문구만 고정 — 버튼 둘 다 "취소"가 되는 혼동을 피한다.)

- [ ] **Step 1: 실패하는 테스트 작성** (`ConfirmDialog.test.tsx`; jsdom에 `showModal`이 없으면 테스트 setup에서 `HTMLDialogElement.prototype.showModal/close`를 스텁 — 기존 ShareForm 테스트가 쓰는 방식을 따른다)
  - `배경을_누르면_onClose`: dialog 요소에 `fireEvent.click(dialog)` → onClose 1회.
  - `내용을_누르면_닫히지_않는다`: 제목 클릭 → onClose 0회.
  - `Esc면_onClose`: `fireEvent(dialog, new Event("cancel", {cancelable:true}))` → onClose 1회.
  - `진행_중이면_배경·Esc로_닫히지_않고_버튼이_비활성`: pending=true → onClose 0회, 두 버튼 disabled.
  - `AppNav.test.tsx` `로그아웃은_확인해야_한다`: [로그아웃] 클릭 → "로그아웃할까요?" 보임, DELETE 안 감; 모달 [로그아웃] → `DELETE /api/v1/sessions` 호출; [취소] → 모달 닫힘.
  - `PostOverview.test.tsx`: 기존 `window.confirm` 스텁 테스트를 모달 클릭으로 바꾸고, `게시_확인_모달_배경_클릭이면_요청_없음` 추가.
- [ ] **Step 2: 실패 확인** — `cd apps/web && pnpm test` → FAIL.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test` 통과.
- [ ] **Step 5: 커밋** — `Feat(web) : 공용 확인 모달과 로그아웃 확인`

### Task 4: 되돌리기 훅과 공용 사진 부품 (웹)

**Files:**
- Create: `apps/web/hooks/use-undoable.ts`, `apps/web/hooks/use-undoable.test.ts`
- Create: `apps/web/components/ui/UndoRedoBar.tsx`
- Create: `apps/web/components/ui/AddPhotoTile.tsx`, `apps/web/components/ui/AddPhotoTile.test.tsx`
- Create: `apps/web/components/ui/BlobImage.tsx` (ShareForm의 `PhotoPreview` ref 콜백 방식을 옮김)

**Interfaces:**
- Produces:
  - `useUndoable<T>(initial: T, limit = 50): { state: T; set: (next: T | ((prev: T) => T)) => void; undo: () => void; redo: () => void; canUndo: boolean; canRedo: boolean }` — 스냅숏 스택(past/present/future). `set`은 present를 past에 넣고 future를 비우며 past가 limit를 넘으면 가장 오래된 것을 버린다. `Object.is(next, present)`면 기록하지 않는다. 순수 리듀서 `undoableReducer`도 export해 테스트한다.
  - `UndoRedoBar({ canUndo, canRedo, disabled, onUndo, onRedo })` — 버튼 [되돌리기] [다시 하기], `disabled || !canX`이면 비활성, 44px 이상.
  - `AddPhotoTile({ remaining: number; onAdd: (files: File[]) => void; onOverflow: (skipped: number) => void; disabled?: boolean })` — `remaining <= 0`이면 아무것도 그리지 않음. 버튼(aria-label "사진 추가", 내용 "+")이 숨은 `<input type="file" accept="image/*" multiple>`를 연다. 고른 파일 앞에서 `remaining`장만 `onAdd`, 남으면 `onOverflow(skipped)`. 선택 후 input 값을 비워 같은 파일을 다시 고를 수 있게 한다.
  - `overflowMessage(skipped: number): string` (AddPhotoTile.tsx에서 export) = `` `사진은 10장까지 넣을 수 있어 ${skipped}장은 넣지 않았습니다.` ``
  - `BlobImage({ file: File; alt: string; className?: string })`.

- [ ] **Step 1: 실패하는 테스트 작성**
  - `use-undoable.test.ts`: `set 두 번 뒤 undo 두 번이면 처음 값, canUndo=false`; `undo 뒤 set 하면 canRedo=false`; `limit=50에서 set 51번 뒤 undo는 50번까지만`; `같은 값 set은 기록 안 함`.
  - `AddPhotoTile.test.tsx`: `remaining=1에 3장 고르면 onAdd 1장, onOverflow(2)`; `remaining=0이면 "사진 추가" 버튼 없음`; `overflowMessage(2) === "사진은 10장까지 넣을 수 있어 2장은 넣지 않았습니다."`.
- [ ] **Step 2: 실패 확인** — `pnpm test` → FAIL.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test`.
- [ ] **Step 5: 커밋** — `Feat(web) : 되돌리기 훅과 사진 추가 타일`

### Task 5: 공유 화면 — 빨간 빼기·추가·되돌리기 (웹)

**Files:**
- Modify: `apps/web/components/share/ShareForm.tsx`
- Test: `apps/web/components/share/ShareForm.test.tsx`

**Interfaces:**
- Consumes: Task 3 `ConfirmDialog`, Task 4 `useUndoable`, `UndoRedoBar`, `AddPhotoTile`, `overflowMessage`, `BlobImage`.
- 상태: `useUndoable<{ photos: SharedPhoto[]; rating: number | null }>`. 공유 사진을 읽은 뒤의 초기값은 기록이 아니다(읽기 전엔 렌더 안 하므로 읽은 뒤 훅을 가진 하위 컴포넌트를 마운트하는 식으로 초기화). 새로 더한 사진 id는 기존 최대 id 다음부터. 메모는 일반 `useState`.
- [빼기]: 각 사진 `<li class="relative">` 안 오른쪽 위 절대 위치, `bg-red-600 text-white rounded-full min-h-11 min-w-11`, `aria-label="사진 N 빼기"`, 1장이면 비활성. 누르면 "이 사진을 뺄까요?" [빼기][취소] 모달 → 확인 시 그 사진만 뺀다(한 칸 기록).
- [+]: 그리드 마지막 칸, `remaining = 10 - photos.length`. 넘친 경우 안내 문구를 `role="status"`로 보이고, 다음 조작 때 지운다.
- [되돌리기][다시 하기]: 사진 그리드 위. 전송 중(`sending`)이면 비활성.

- [ ] **Step 1: 실패하는 테스트 작성** (기존 `readSharedPhotos` mock 방식 유지)
  - `빼기는_확인해야_뺀다`: 사진 3장 → "사진 2 빼기" → 모달 [취소] → 3장 그대로; 다시 → [빼기] → 2장.
  - `빼기_되돌리기면_원래_자리로`: 3장 중 2번째 빼기 → [되돌리기] → 3장이고 alt 순서가 원래와 같다 → [다시 하기] → 2장.
  - `별점도_되돌린다`: 별점 4 선택 → [되돌리기] → 별점 없음.
  - `메모는_되돌리지_않는다`: 메모 입력 후 [되돌리기]는 비활성.
  - `추가하면_끝에_붙고_넘치면_안내`: 9장 → 3장 선택 → 10장, "사진은 10장까지 넣을 수 있어 2장은 넣지 않았습니다." 보임, "사진 추가" 버튼 없음.
  - `보낼_때_추가한_사진도_FormData에_순서대로`: 2장 + 1장 추가 → 보내기 → 확인 → FormData `photos` 3개, 마지막이 추가한 파일.
- [ ] **Step 2: 실패 확인** — `pnpm test` → FAIL.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test`.
- [ ] **Step 5: 커밋** — `Feat(web) : 공유 화면 사진 추가와 되돌리기`

### Task 6: 수정 화면 — 삭제 확인·추가·되돌리기·multipart 저장 (웹)

**Files:**
- Create: `apps/web/components/post-edit/photo-items.ts`, `apps/web/components/post-edit/photo-items.test.ts`
- Modify: `apps/web/types/api/post.ts` (`PhotoEdit`)
- Modify: `apps/web/hooks/use-update-post.ts`
- Modify: `apps/web/components/post-edit/PhotoOrderList.tsx`
- Modify: `apps/web/components/post-edit/PostEditForm.tsx`
- Test: `PhotoOrderList.test.tsx`, `PostEditForm.test.tsx`

**Interfaces:**
- Consumes: Task 2 API 형식, Task 3·4 부품.
- Produces:
  - `types/api/post.ts`: `export type PhotoEdit = { id: number; fitMode: FitMode } | { newIndex: number; fitMode: FitMode };`
  - `photo-items.ts`:
    - `export type PhotoItem = { key: string; fitMode: FitMode } & ({ kind: "saved"; id: number; url: string } | { kind: "new"; file: File })`
    - `fromSaved(photos: PostPhotoResponse[]): PhotoItem[]` — key `saved-{id}`
    - `newItems(files: File[], startSeq: number): PhotoItem[]` — key `new-{seq}`, fitMode `"CROP"`
    - `toUpdatePhotos(items: PhotoItem[]): { photos: PhotoEdit[]; newPhotos: File[] }` — 새 사진은 등장 순서대로 `newIndex` 0,1,2…
  - `useUpdatePost(id)` mutationFn 인자 `{ request: PostUpdateRequest; newPhotos: File[] }` → `FormData`에 `request`를 `new Blob([JSON.stringify(request)], { type: "application/json" })`로, 파일은 `newPhotos` 이름으로 순서대로. `method: "PUT"`, Content-Type 헤더는 직접 넣지 않는다.
  - `PhotoOrderList` props: `photos: PhotoItem[]`, `version`, `onChange(PhotoItem[])`, `onBusyChange?`, `onAdd?: (files: File[]) => void`, `onOverflow?: (skipped: number) => void`. 키는 `item.key`, 저장된 사진은 `${url}?v=${version}`, 새 사진은 `BlobImage`. [삭제]는 "이 사진을 뺄까요?" [빼기][취소] 모달 확인 뒤 기존 delete 애니메이션을 시작. 목록 끝에 `AddPhotoTile`(remaining = 10 - photos.length).
- `PostEditForm.Editor`: `useUndoable<{ photos: PhotoItem[]; rating: number | null }>({ photos: fromSaved(post.photos), rating: post.rating })`. `UndoRedoBar`는 사진 목록 위, `photosBusy || onSave.isPending`이면 비활성. 저장은 `toUpdatePhotos` 결과로 `mutateAsync({ request, newPhotos })`. 실패하면 상태·기록을 건드리지 않는다(Editor는 성공 시에만 다시 만들어진다).

- [ ] **Step 1: 실패하는 테스트 작성**
  - `photo-items.test.ts`: `[saved1, new(a), saved2, new(b)]` → `photos=[{id:1},{newIndex:0},{id:2},{newIndex:1}]`(fitMode 포함), `newPhotos=[a,b]`.
  - `PhotoOrderList.test.tsx` `삭제는_확인해야_한다`: [삭제] → 모달 [취소] → onChange 안 불림; [삭제] → [빼기] → (모션 감소 or 타이머 진행 후) onChange가 그 사진 뺀 목록으로.
  - `PostEditForm.test.tsx`:
    - `새_사진을_더해_저장하면_multipart로_보낸다`: 파일 1장 추가 → [저장] → fetch body가 FormData, `request` 파트 JSON의 photos 마지막이 `{newIndex:0, fitMode:"CROP"}`, `newPhotos` 1개.
    - `저장_실패해도_새_사진과_되돌리기가_남는다`: 409 응답 → 새 사진 미리보기 그대로, [되돌리기] 활성, 다시 [저장] 시 같은 파일을 보냄.
    - `삭제_뒤_되돌리기면_다시_보인다`: 삭제 확인 → [되돌리기] → 원래 장수·순서.
    - `애니메이션_중엔_되돌리기_비활성`: 삭제 확인 직후(타이머 진행 전) [되돌리기] disabled.
- [ ] **Step 2: 실패 확인** — `pnpm test` → FAIL.
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인** — `pnpm lint && pnpm test`.
- [ ] **Step 5: 커밋** — `Feat(web) : 수정 화면 사진 추가와 되돌리기`
