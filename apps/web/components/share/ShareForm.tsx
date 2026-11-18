"use client";

import { useRouter } from "next/navigation";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
} from "react";

import { RatingInput } from "@/components/ui/RatingInput";
import { useCreatePost } from "@/hooks/use-create-post";
import { useSession } from "@/hooks/use-session";
import { ApiError } from "@/lib/api";
import { readSharedPhotos } from "@/lib/shared-photos";

const MAX_PHOTOS = 10;
const MAX_MEMO_LENGTH = 2000;
const MEMO_PREVIEW_LENGTH = 40;
const FALLBACK_ERROR = "전송에 실패했습니다. 잠시 후 다시 시도해 주세요.";
const SESSION_ERROR =
  "로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.";

// URL 생성과 revoke를 ref 콜백 한 곳에 둬서 StrictMode 재마운트에도 안전하다.
function PhotoPreview({ file, index }: { file: File; index: number }) {
  const attach = useCallback(
    (img: HTMLImageElement) => {
      const url = URL.createObjectURL(file);
      img.src = url;
      return () => URL.revokeObjectURL(url);
    },
    [file],
  );
  return (
    // eslint-disable-next-line @next/next/no-img-element -- blob 미리보기
    <img
      ref={attach}
      alt={`공유한 사진 ${index}`}
      className="aspect-square w-full rounded-md object-cover"
    />
  );
}

type SharedPhoto = { id: number; file: File };

// 네이티브 <dialog>를 모달로 연다. Esc로 닫히면 onClose로 상태를 맞춘다.
function ConfirmDialog({
  photoCount,
  rating,
  memo,
  pending,
  onConfirm,
  onClose,
}: {
  photoCount: number;
  rating: number | null;
  memo: string;
  pending: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);
  const memoPreview =
    memo === ""
      ? "없음"
      : memo.length > MEMO_PREVIEW_LENGTH
        ? `${memo.slice(0, MEMO_PREVIEW_LENGTH)}…`
        : memo;
  return (
    <dialog
      ref={ref}
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) onClose();
      }}
      aria-labelledby="share-confirm-title"
      className="m-auto w-[min(90vw,24rem)] rounded-lg p-5 backdrop:bg-black/40"
    >
      <h2 id="share-confirm-title" className="mb-3 font-semibold">
        이대로 보낼까요?
      </h2>
      <dl className="mb-4 flex flex-col gap-1 text-sm">
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
      <div className="flex gap-2">
        <button
          type="button"
          onClick={onClose}
          disabled={pending}
          className="flex-1 rounded-md border border-neutral-300 px-4 py-2.5 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
        >
          돌아가기
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={pending}
          className="flex-1 rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
        >
          보내기
        </button>
      </div>
    </dialog>
  );
}

export function ShareForm() {
  const router = useRouter();
  const session = useSession();
  const createPost = useCreatePost();
  const [photos, setPhotos] = useState<SharedPhoto[] | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [rating, setRating] = useState<number | null>(null);
  const [memo, setMemo] = useState("");

  useEffect(() => {
    let cancelled = false;
    // 읽지 못하면 공유된 사진이 없는 것으로 본다.
    readSharedPhotos()
      .catch(() => [])
      .then((files) => {
        if (!cancelled) setPhotos(files.map((file, id) => ({ id, file })));
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

  if (!session.isSuccess || photos === null) return null;

  if (photos.length === 0) {
    return (
      <p className="text-neutral-700">
        공유된 사진이 없습니다. 갤러리에서 사진을 골라 k-drinker로 공유해
        주세요.
      </p>
    );
  }

  const tooMany = photos.length > MAX_PHOTOS;
  // 성공한 뒤에도 화면 이동이 끝날 때까지 잠가 둔다. 다시 누르면 게시물이 하나 더 생긴다.
  const sending = createPost.isPending || createPost.isSuccess;

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
      <ul className="grid grid-cols-3 gap-2">
        {photos.map(({ id, file }, i) => (
          <li key={id} className="flex flex-col gap-1">
            <PhotoPreview file={file} index={i + 1} />
            <button
              type="button"
              disabled={photos.length <= 1}
              aria-label={`사진 ${i + 1} 빼기`}
              onClick={() =>
                setPhotos(photos.filter((photo) => photo.id !== id))
              }
              className="rounded-md border border-neutral-300 px-2 py-1 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
            >
              빼기
            </button>
          </li>
        ))}
      </ul>
      <RatingInput value={rating} onChange={setRating} disabled={tooMany} />
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
          photoCount={photos.length}
          rating={rating}
          memo={memo}
          pending={sending}
          onConfirm={send}
          onClose={() => setConfirming(false)}
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
