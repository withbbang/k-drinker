"use client";

import type { PostPhotoResponse } from "@/types/api/post";

type PhotoOrderListProps = {
  photos: PostPhotoResponse[];
  version: number;
  onChange: (photos: PostPhotoResponse[]) => void;
};

const BUTTON =
  "rounded-md border border-neutral-300 px-3 py-2 text-sm disabled:opacity-40";

export function PhotoOrderList({
  photos,
  version,
  onChange,
}: PhotoOrderListProps) {
  const move = (index: number, delta: number) => {
    const next = [...photos];
    [next[index], next[index + delta]] = [next[index + delta], next[index]];
    onChange(next);
  };

  return (
    <ul className="flex flex-col gap-3">
      {photos.map((photo, i) => (
        <li key={photo.id} className="flex flex-col gap-2">
          {/* eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL */}
          <img
            src={`${photo.url}?v=${version}`}
            alt={`사진 ${i + 1}`}
            className="aspect-square w-full rounded-md object-cover"
          />
          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              className={BUTTON}
              disabled={i === 0}
              onClick={() => move(i, -1)}
            >
              위로
            </button>
            <button
              type="button"
              className={BUTTON}
              disabled={i === photos.length - 1}
              onClick={() => move(i, 1)}
            >
              아래로
            </button>
            <button
              type="button"
              className={BUTTON}
              disabled={photos.length === 1}
              onClick={() => onChange(photos.filter((p) => p.id !== photo.id))}
            >
              삭제
            </button>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={photo.fitMode === "PAD"}
                onChange={(event) =>
                  onChange(
                    photos.map((p) =>
                      p.id === photo.id
                        ? {
                            ...p,
                            fitMode: event.target.checked ? "PAD" : "CROP",
                          }
                        : p,
                    ),
                  )
                }
              />
              여백 넣기
            </label>
          </div>
        </li>
      ))}
    </ul>
  );
}
