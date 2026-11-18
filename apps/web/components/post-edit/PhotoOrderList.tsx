"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import type { PostPhotoResponse } from "@/types/api/post";

type PhotoOrderListProps = {
  photos: PostPhotoResponse[];
  version: number;
  onChange: (photos: PostPhotoResponse[]) => void;
};

// 진행 중인 애니메이션 (한 번에 하나만)
type Anim =
  | { kind: "move"; from: number; to: number; fromDy: number; toDy: number }
  | { kind: "delete"; id: number }
  | { kind: "fit"; id: number };

// 전환은 200ms(duration-200). transitionend가 오지 않을 때를 위한 안전장치
const FALLBACK_MS = 250;
// ul의 gap-3(12px)
const GAP_PX = 12;

const BUTTON = "min-h-11 min-w-11 px-3 text-sm text-white disabled:opacity-40";

// matchMedia가 없는 환경(jsdom 등)에서는 모션 감소가 아닌 것으로 본다
function prefersReducedMotion(): boolean {
  if (
    typeof window === "undefined" ||
    typeof window.matchMedia !== "function"
  ) {
    return false;
  }
  return (
    window.matchMedia("(prefers-reduced-motion: reduce)")?.matches ?? false
  );
}

export function PhotoOrderList({
  photos,
  version,
  onChange,
}: PhotoOrderListProps) {
  const [anim, setAnim] = useState<Anim | null>(null);
  const items = useRef<(HTMLLIElement | null)[]>([]);
  const commit = useRef<(() => void) | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const finish = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    const run = commit.current;
    commit.current = null;
    if (!run) return;
    run();
    setAnim(null);
  }, []);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
    },
    [],
  );

  const play = (next: Anim, apply: () => PostPhotoResponse[]) => {
    if (prefersReducedMotion()) {
      onChange(apply());
      return;
    }
    commit.current = () => onChange(apply());
    setAnim(next);
    timer.current = setTimeout(finish, FALLBACK_MS);
  };

  const move = (index: number, delta: number) => {
    const to = index + delta;
    const height = (i: number) =>
      (items.current[i]?.offsetHeight ?? 0) + GAP_PX;
    // 두 사진이 서로의 높이만큼 반대 방향으로 움직인다
    play(
      {
        kind: "move",
        from: index,
        to,
        fromDy: delta * height(to),
        toDy: -delta * height(index),
      },
      () => {
        const next = [...photos];
        [next[index], next[to]] = [next[to], next[index]];
        return next;
      },
    );
  };

  const locked = anim !== null;

  const liStyle = (i: number, id: number) => {
    if (anim?.kind === "move" && (i === anim.from || i === anim.to)) {
      const dy = i === anim.from ? anim.fromDy : anim.toDy;
      return { transform: `translateY(${dy}px)` };
    }
    if (anim?.kind === "delete" && anim.id === id) {
      return { opacity: 0, transform: "scale(0.9)" };
    }
    return undefined;
  };

  return (
    <ul className="flex flex-col gap-3">
      {photos.map((photo, i) => {
        const pad = photo.fitMode === "PAD";
        // 여백 토글 중에는 바뀔 값을 미리 보여 준다
        const showPad =
          anim?.kind === "fit" && anim.id === photo.id ? !pad : pad;
        return (
          <li
            key={photo.id}
            ref={(el) => {
              items.current[i] = el;
            }}
            className="transition-[transform,opacity] duration-200 ease-out"
            style={liStyle(i, photo.id)}
            onTransitionEnd={(event) => {
              if (event.target === event.currentTarget) finish();
            }}
          >
            <div className="relative overflow-hidden rounded-md">
              {/* eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL */}
              <img
                src={`${photo.url}?v=${version}`}
                alt={`사진 ${i + 1}`}
                className={`aspect-square w-full bg-black transition-transform duration-200 ${
                  showPad ? "object-contain" : "object-cover"
                } ${anim?.kind === "fit" && anim.id === photo.id ? "scale-95" : ""}`}
              />
              <div className="absolute inset-x-0 bottom-0 flex flex-wrap items-center justify-around bg-black/50">
                <button
                  type="button"
                  className={BUTTON}
                  disabled={locked || i === 0}
                  onClick={() => move(i, -1)}
                >
                  위로
                </button>
                <button
                  type="button"
                  className={BUTTON}
                  disabled={locked || i === photos.length - 1}
                  onClick={() => move(i, 1)}
                >
                  아래로
                </button>
                <button
                  type="button"
                  className={BUTTON}
                  disabled={locked || photos.length === 1}
                  onClick={() =>
                    play({ kind: "delete", id: photo.id }, () =>
                      photos.filter((p) => p.id !== photo.id),
                    )
                  }
                >
                  삭제
                </button>
                <button
                  type="button"
                  className={BUTTON}
                  disabled={locked}
                  onClick={() =>
                    play({ kind: "fit", id: photo.id }, () =>
                      photos.map((p) =>
                        p.id === photo.id
                          ? { ...p, fitMode: pad ? "CROP" : "PAD" }
                          : p,
                      ),
                    )
                  }
                >
                  {pad ? "여백 빼기" : "여백 넣기"}
                </button>
              </div>
            </div>
          </li>
        );
      })}
    </ul>
  );
}
