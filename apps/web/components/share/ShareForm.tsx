"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

import { RatingInput } from "@/components/ui/RatingInput";
import { useCreatePost } from "@/hooks/use-create-post";
import { useSession } from "@/hooks/use-session";
import { ApiError } from "@/lib/api";
import { readSharedPhotos } from "@/lib/shared-photos";

const MAX_PHOTOS = 10;
const MAX_MEMO_LENGTH = 2000;
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

export function ShareForm() {
  const session = useSession();
  const createPost = useCreatePost();
  const [photos, setPhotos] = useState<File[] | null>(null);
  const [rating, setRating] = useState<number | null>(null);
  const [memo, setMemo] = useState("");

  useEffect(() => {
    let cancelled = false;
    // 읽지 못하면 공유된 사진이 없는 것으로 본다.
    readSharedPhotos()
      .catch(() => [])
      .then((files) => {
        if (!cancelled) setPhotos(files);
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

  if (createPost.isSuccess) {
    return (
      <p role="status">
        접수했습니다. 분석이 끝나면 텔레그램으로 개요를 보내 드립니다.
      </p>
    );
  }

  if (photos.length === 0) {
    return (
      <p className="text-neutral-700">
        공유된 사진이 없습니다. 갤러리에서 사진을 골라 k-drinker로 공유해
        주세요.
      </p>
    );
  }

  const tooMany = photos.length > MAX_PHOTOS;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const body = new FormData();
    photos.forEach((file) => body.append("photos", file));
    body.append("memo", memo);
    if (rating !== null) body.append("rating", String(rating));
    body.append("source", "SHARE");
    createPost.mutate(body);
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <ul className="grid grid-cols-3 gap-2">
        {photos.map((file, i) => (
          <li key={`${file.name}-${i}`}>
            <PhotoPreview file={file} index={i + 1} />
          </li>
        ))}
      </ul>
      <RatingInput value={rating} onChange={setRating} />
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">메모</span>
        <input
          type="text"
          value={memo}
          maxLength={MAX_MEMO_LENGTH}
          onChange={(event) => setMemo(event.target.value)}
          className="rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        />
      </label>
      {tooMany && (
        <p className="text-sm text-red-700">
          사진은 1장 이상 10장 이하로 올려 주세요.
        </p>
      )}
      <button
        type="submit"
        disabled={tooMany || createPost.isPending}
        className="rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
      >
        보내기
      </button>
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
