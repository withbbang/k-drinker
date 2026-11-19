"use client";

import { useState } from "react";

import type { PostPhotoResponse } from "@/types/api/post";

// 사진을 4:5로 한 장씩 옆으로 넘긴다. 번호는 alt에 있으므로 배지는 화면용이다.
export function PhotoCarousel({
  photos,
  version,
}: {
  photos: PostPhotoResponse[];
  version: number;
}) {
  const [current, setCurrent] = useState(0);
  const total = photos.length;

  return (
    <div className="relative">
      <div
        role="region"
        aria-label="사진"
        tabIndex={0}
        onScroll={(event) => {
          const { scrollLeft, clientWidth } = event.currentTarget;
          if (clientWidth > 0) setCurrent(Math.round(scrollLeft / clientWidth));
        }}
        className="flex snap-x snap-mandatory overflow-x-auto [scrollbar-width:none] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
      >
        {photos.map((photo, i) => (
          <div
            key={photo.id}
            className="aspect-[4/5] w-full shrink-0 snap-center bg-surface"
          >
            {/* eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL */}
            <img
              src={`${photo.url}?v=${version}`}
              alt={`사진 ${i + 1} / ${total}`}
              className="size-full object-cover"
            />
          </div>
        ))}
      </div>
      {total > 1 && (
        <span
          aria-hidden="true"
          className="absolute right-2 top-2 rounded-full bg-bg/80 px-2 text-xs text-text tabular-nums"
        >
          {Math.min(current, total - 1) + 1} / {total}
        </span>
      )}
    </div>
  );
}
