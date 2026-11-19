"use client";

import { useRouter } from "next/navigation";
import {
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";

import { overflowMessage } from "@/components/ui/AddPhotoTile";
import { AutoTextarea } from "@/components/ui/AutoTextarea";
import { BlobImage } from "@/components/ui/BlobImage";
import { buttonClass } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { PhotoStrip } from "@/components/ui/PhotoStrip";
import { RatingInput } from "@/components/ui/RatingInput";
import { Toast } from "@/components/ui/Toast";
import { TopBar } from "@/components/ui/TopBar";
import { UndoRedoBar } from "@/components/ui/UndoRedoBar";
import { useCreatePost } from "@/hooks/use-create-post";
import { useSession } from "@/hooks/use-session";
import { useToast } from "@/hooks/use-toast";
import { useUndoable } from "@/hooks/use-undoable";
import { ApiError } from "@/lib/api";
import { readSharedPhotos } from "@/lib/shared-photos";

const MAX_PHOTOS = 10;
const MAX_MEMO_LENGTH = 2000;
const MEMO_PREVIEW_LENGTH = 40;
const FALLBACK_ERROR = "전송에 실패했어요. 잠시 후 다시 시도해 주세요.";
const SESSION_ERROR =
  "로그인 상태를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.";

type SharedPhoto = { id: number; file: File };
const TITLE = "새 기록";

type EditState = {
  photos: SharedPhoto[];
  rating: number | null;
  memo: string;
};

// 보내기 전에 보여 주는 요약.
function SendSummary({
  photoCount,
  rating,
  memo,
}: {
  photoCount: number;
  rating: number | null;
  memo: string;
}) {
  const memoPreview =
    memo === ""
      ? "없음"
      : memo.length > MEMO_PREVIEW_LENGTH
        ? `${memo.slice(0, MEMO_PREVIEW_LENGTH)}…`
        : memo;
  return (
    <dl className="flex flex-col gap-1 text-sm">
      <div className="flex gap-2">
        <dt className="text-muted">사진</dt>
        <dd>사진 {photoCount}장</dd>
      </div>
      <div className="flex gap-2">
        <dt className="text-muted">별점</dt>
        <dd>{rating === null ? "없음" : rating.toFixed(1)}</dd>
      </div>
      <div className="flex gap-2">
        <dt className="text-muted">메모</dt>
        <dd className="whitespace-pre-wrap break-all">{memoPreview}</dd>
      </div>
    </dl>
  );
}

// 편집기를 띄우기 전 상태(오류·사진 없음)에도 같은 상단 바를 둔다.
function Shell({ children }: { children: ReactNode }) {
  return (
    <>
      <TopBar title={TITLE} backHref="/" />
      <div className="flex flex-col gap-3 px-4 pt-5 text-text">{children}</div>
    </>
  );
}

export function ShareForm() {
  const session = useSession();
  const [initial, setInitial] = useState<SharedPhoto[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    // 읽지 못하면 공유된 사진이 없는 것으로 본다.
    readSharedPhotos()
      .catch(() => [])
      .then((files) => {
        if (!cancelled) setInitial(files.map((file, id) => ({ id, file })));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 401은 QueryProvider가 로그인 화면으로 보낸다. 그 밖의 오류는 다시 시도하게 한다.
  if (
    session.isError &&
    !(
      session.error instanceof ApiError &&
      session.error.code === "AUTH_REQUIRED"
    )
  ) {
    return (
      <Shell>
        <p role="alert" className="text-sm text-danger">
          {SESSION_ERROR}
        </p>
        <button
          type="button"
          onClick={() => session.refetch()}
          className={buttonClass("secondary", "self-start")}
        >
          다시 시도
        </button>
      </Shell>
    );
  }

  if (!session.isSuccess || initial === null) return null;

  if (initial.length === 0) {
    return (
      <Shell>
        <p>
          공유된 사진이 없어요. 갤러리에서 사진을 골라 k-drinker로 공유해
          주세요.
        </p>
      </Shell>
    );
  }

  // 읽은 뒤에 마운트해서 초기 사진은 되돌리기 기록이 아니다.
  return <ShareEditor initialPhotos={initial} />;
}

function ShareEditor({ initialPhotos }: { initialPhotos: SharedPhoto[] }) {
  const router = useRouter();
  const formId = useId();
  const createPost = useCreatePost();
  const { state, set, setTyping, endTyping, undo, redo, canUndo, canRedo } =
    useUndoable<EditState>({
      photos: initialPhotos,
      rating: null,
      memo: "",
    });
  const { photos, rating, memo } = state;
  const { toast, show, dismiss } = useToast();
  const [confirming, setConfirming] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  // 사진을 끄는 동안은 순서가 아직 photos에 반영되지 않았으므로 되돌리기를 막는다.
  const [photosBusy, setPhotosBusy] = useState(false);

  const tooMany = photos.length > MAX_PHOTOS;
  // 성공한 뒤에도 화면 이동이 끝날 때까지 잠가 둔다. 다시 누르면 게시물이 하나 더 생긴다.
  const sending = createPost.isPending || createPost.isSuccess;
  const locked = photosBusy || sending;
  // 토스트의 실행 취소는 뜰 때의 콜백을 들고 있으므로 지금 잠겼는지는 ref로 본다.
  const lockedRef = useRef(locked);
  useEffect(() => {
    lockedRef.current = locked;
  }, [locked]);

  // 어떤 변경이든 지난 알림과 "사진을 뺐어요" 토스트는 닫는다.
  const clearMessages = () => {
    setNotice(null);
    dismiss();
  };
  const change = (next: (prev: EditState) => EditState) => {
    clearMessages();
    set(next);
  };
  // 보낸 요청과 화면이 어긋나거나 끄는 중 순서가 덮어써지지 않게 막는다.
  const handleUndo = () => {
    if (lockedRef.current || locked) return;
    clearMessages();
    undo();
  };
  const handleRedo = () => {
    if (lockedRef.current || locked) return;
    clearMessages();
    redo();
  };

  // 새 사진 id 순번. 공유된 사진 다음부터 쓰고, 되돌려도 다시 쓰지 않아 id가 겹치지 않는다(수정 화면과 같은 방식).
  const seq = useRef(initialPhotos.length);

  const addPhotos = (files: File[]) => {
    const start = seq.current;
    seq.current += files.length;
    const added = files.map((file, i) => ({ id: start + i, file }));
    change((prev) => ({ ...prev, photos: [...prev.photos, ...added] }));
  };

  const removePhoto = (key: string) => {
    change((prev) => ({
      ...prev,
      photos: prev.photos.filter((photo) => String(photo.id) !== key),
    }));
    show("사진을 뺐어요", { label: "실행 취소", onAction: handleUndo });
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tooMany && !locked) setConfirming(true);
  };

  const send = () => {
    // 보내기를 시작하면 "사진을 뺐어요" 토스트의 실행 취소는 더 쓸 수 없다.
    dismiss();
    const body = new FormData();
    photos.forEach(({ file }) => body.append("photos", file));
    body.append("memo", memo);
    if (rating !== null) body.append("rating", String(rating));
    body.append("source", "SHARE");
    createPost.mutate(body, {
      onSuccess: (created) => router.replace(`/posts/${created.id}`),
      onError: () => setConfirming(false),
    });
  };

  return (
    <>
      <TopBar
        title={TITLE}
        backHref="/"
        actions={
          <>
            <UndoRedoBar
              canUndo={canUndo}
              canRedo={canRedo}
              disabled={locked}
              onUndo={handleUndo}
              onRedo={handleRedo}
            />
            <button
              type="submit"
              form={formId}
              disabled={tooMany || locked}
              className={buttonClass("primary", "ml-1 px-4")}
            >
              보내기
            </button>
          </>
        }
      />
      <form
        id={formId}
        onSubmit={handleSubmit}
        className="flex flex-col gap-6 px-4 pb-24 pt-5"
      >
        <PhotoStrip
          items={photos}
          getKey={(photo) => String(photo.id)}
          renderImage={(photo, className) => (
            <BlobImage
              file={photo.file}
              alt={`사진 ${photos.indexOf(photo) + 1}`}
              className={className}
            />
          )}
          disabled={sending}
          max={MAX_PHOTOS}
          onReorder={(next) => change((prev) => ({ ...prev, photos: next }))}
          onRemove={removePhoto}
          onBusyChange={(busy) => {
            if (busy) dismiss();
            setPhotosBusy(busy);
          }}
          onAdd={addPhotos}
          onOverflow={(skipped) => setNotice(overflowMessage(skipped))}
        />
        {tooMany && (
          <p className="text-sm text-danger">
            {`사진을 ${MAX_PHOTOS}장 이하로 줄여 주세요 (지금 ${photos.length}장)`}
          </p>
        )}
        {notice !== null && (
          <p role="status" className="text-sm text-muted">
            {notice}
          </p>
        )}
        <RatingInput
          value={rating}
          onChange={(value) => change((prev) => ({ ...prev, rating: value }))}
          disabled={tooMany}
        />
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-text">메모</span>
          <AutoTextarea
            value={memo}
            disabled={tooMany}
            maxLength={MAX_MEMO_LENGTH}
            onChange={(event) => {
              const value = event.target.value;
              clearMessages();
              setTyping("memo", (prev) => ({ ...prev, memo: value }));
            }}
            onBlur={endTyping}
          />
        </label>
        {createPost.isError && (
          <p role="alert" className="text-sm text-danger">
            {createPost.error instanceof ApiError
              ? createPost.error.message
              : FALLBACK_ERROR}
          </p>
        )}
      </form>
      {confirming && (
        <ConfirmDialog
          title="이대로 보낼까요?"
          confirmLabel="보내기"
          cancelLabel="돌아가기"
          pending={sending}
          onConfirm={send}
          onClose={() => setConfirming(false)}
        >
          <SendSummary photoCount={photos.length} rating={rating} memo={memo} />
        </ConfirmDialog>
      )}
      <Toast toast={toast} onDismiss={dismiss} />
    </>
  );
}
