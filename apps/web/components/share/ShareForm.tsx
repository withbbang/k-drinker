"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";

import { AddPhotoTile, overflowMessage } from "@/components/ui/AddPhotoTile";
import { BlobImage } from "@/components/ui/BlobImage";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { RatingInput } from "@/components/ui/RatingInput";
import { UndoRedoBar } from "@/components/ui/UndoRedoBar";
import { useCreatePost } from "@/hooks/use-create-post";
import { useSession } from "@/hooks/use-session";
import { useUndoable } from "@/hooks/use-undoable";
import { ApiError } from "@/lib/api";
import { readSharedPhotos } from "@/lib/shared-photos";

const MAX_PHOTOS = 10;
const MAX_MEMO_LENGTH = 2000;
const MEMO_PREVIEW_LENGTH = 40;
const FALLBACK_ERROR = "전송에 실패했습니다. 잠시 후 다시 시도해 주세요.";
const SESSION_ERROR =
  "로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.";

type SharedPhoto = { id: number; file: File };
type EditState = { photos: SharedPhoto[]; rating: number | null };

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
        <dt className="text-neutral-600">사진</dt>
        <dd>사진 {photoCount}장</dd>
      </div>
      <div className="flex gap-2">
        <dt className="text-neutral-600">별점</dt>
        <dd>{rating === null ? "없음" : rating.toFixed(1)}</dd>
      </div>
      <div className="flex gap-2">
        <dt className="text-neutral-600">메모</dt>
        <dd className="break-all">{memoPreview}</dd>
      </div>
    </dl>
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
      <div className="flex flex-col gap-3">
        <p role="alert" className="text-sm text-red-700">
          {SESSION_ERROR}
        </p>
        <button
          type="button"
          onClick={() => session.refetch()}
          className="rounded-md border border-neutral-300 px-4 py-2.5 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        >
          다시 시도
        </button>
      </div>
    );
  }

  if (!session.isSuccess || initial === null) return null;

  if (initial.length === 0) {
    return (
      <p className="text-neutral-700">
        공유된 사진이 없습니다. 갤러리에서 사진을 골라 k-drinker로 공유해
        주세요.
      </p>
    );
  }

  // 읽은 뒤에 마운트해서 초기 사진은 되돌리기 기록이 아니다.
  return <ShareEditor initialPhotos={initial} />;
}

function ShareEditor({ initialPhotos }: { initialPhotos: SharedPhoto[] }) {
  const router = useRouter();
  const createPost = useCreatePost();
  const { state, set, undo, redo, canUndo, canRedo } = useUndoable<EditState>({
    photos: initialPhotos,
    rating: null,
  });
  const { photos, rating } = state;
  const [confirming, setConfirming] = useState(false);
  const [removingId, setRemovingId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [memo, setMemo] = useState("");

  const tooMany = photos.length > MAX_PHOTOS;
  const remaining = MAX_PHOTOS - photos.length;
  // 성공한 뒤에도 화면 이동이 끝날 때까지 잠가 둔다. 다시 누르면 게시물이 하나 더 생긴다.
  const sending = createPost.isPending || createPost.isSuccess;

  const change = (next: (prev: EditState) => EditState) => {
    setNotice(null);
    set(next);
  };

  const addPhotos = (files: File[]) =>
    change((prev) => {
      // 새 id는 기존 최대 id 다음부터 (빼고 되돌려도 겹치지 않는다)
      let nextId = Math.max(-1, ...prev.photos.map((p) => p.id)) + 1;
      const added = files.map((file) => ({ id: nextId++, file }));
      return { ...prev, photos: [...prev.photos, ...added] };
    });

  const removePhoto = () => {
    const id = removingId;
    setRemovingId(null);
    if (id === null) return;
    change((prev) => ({
      ...prev,
      photos: prev.photos.filter((photo) => photo.id !== id),
    }));
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!tooMany && !sending) setConfirming(true);
  };

  const send = () => {
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <UndoRedoBar
        canUndo={canUndo}
        canRedo={canRedo}
        disabled={sending}
        onUndo={() => {
          setNotice(null);
          undo();
        }}
        onRedo={() => {
          setNotice(null);
          redo();
        }}
      />
      <ul className="grid grid-cols-3 gap-2">
        {photos.map(({ id, file }, i) => (
          <li key={id} className="relative">
            <BlobImage
              file={file}
              alt={`공유한 사진 ${i + 1}`}
              className="aspect-square w-full rounded-md object-cover"
            />
            <button
              type="button"
              disabled={photos.length <= 1 || sending}
              aria-label={`사진 ${i + 1} 빼기`}
              onClick={() => setRemovingId(id)}
              className="absolute top-1 right-1 min-h-11 min-w-11 rounded-full bg-red-600 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
            >
              ✕
            </button>
          </li>
        ))}
        {remaining > 0 && (
          <li>
            <AddPhotoTile
              remaining={remaining}
              disabled={sending}
              onAdd={addPhotos}
              onOverflow={(skipped) => setNotice(overflowMessage(skipped))}
            />
          </li>
        )}
      </ul>
      {notice !== null && (
        <p role="status" className="text-sm text-neutral-700">
          {notice}
        </p>
      )}
      <RatingInput
        value={rating}
        onChange={(value) => change((prev) => ({ ...prev, rating: value }))}
        disabled={tooMany}
      />
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">메모</span>
        <input
          type="text"
          value={memo}
          disabled={tooMany}
          maxLength={MAX_MEMO_LENGTH}
          onChange={(event) => setMemo(event.target.value)}
          className="rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        />
      </label>
      <button
        type="submit"
        disabled={tooMany || sending}
        className="rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
      >
        {tooMany
          ? `사진을 ${MAX_PHOTOS}장 이하로 줄여 주세요 (지금 ${photos.length}장)`
          : "보내기"}
      </button>
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
      {removingId !== null && (
        <ConfirmDialog
          title="이 사진을 뺄까요?"
          confirmLabel="빼기"
          onConfirm={removePhoto}
          onClose={() => setRemovingId(null)}
        />
      )}
      {createPost.isError && (
        <p role="alert" className="text-sm text-red-700">
          {createPost.error instanceof ApiError
            ? createPost.error.message
            : FALLBACK_ERROR}
        </p>
      )}
    </form>
  );
}
