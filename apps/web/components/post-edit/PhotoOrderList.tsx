"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import type { PhotoItem } from "@/components/post-edit/photo-items";
import { AddPhotoTile } from "@/components/ui/AddPhotoTile";
import { BlobImage } from "@/components/ui/BlobImage";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

type PhotoOrderListProps = {
  photos: PhotoItem[];
  version: number;
  onChange: (photos: PhotoItem[]) => void;
  // 애니메이션이 끝나야 onChange로 반영되므로, 그동안 부모가 저장을 막을 수 있게 알린다.
  onBusyChange?: (busy: boolean) => void;
  // 주면 목록 끝에 [+] 타일을 둔다.
  onAdd?: (files: File[]) => void;
  onOverflow?: (skipped: number) => void;
};

// 진행 중인 애니메이션 (한 번에 하나만)
type Anim =
  | { kind: "move"; from: number; to: number; fromDy: number; toDy: number }
  | { kind: "delete"; key: string }
  | { kind: "fit"; key: string };

// 전환은 200ms(duration-200). transitionend가 오지 않을 때를 위한 안전장치
const FALLBACK_MS = 250;
// ul의 gap-3(12px)
const GAP_PX = 12;
const MAX_PHOTOS = 10;

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
  onBusyChange,
  onAdd,
  onOverflow,
}: PhotoOrderListProps) {
  const [anim, setAnim] = useState<Anim | null>(null);
  // 빼기 확인 모달을 띄운 사진
  const [removingKey, setRemovingKey] = useState<string | null>(null);
  // 순서를 바꾼 렌더에서는 transform을 전환 없이 0으로 돌린다. 안 그러면 제자리로 한 번 더 미끄러진다.
  const [settling, setSettling] = useState(false);
  const items = useRef<(HTMLLIElement | null)[]>([]);
  const commit = useRef<(() => void) | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const frame = useRef<number | null>(null);

  const finish = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = null;
    const run = commit.current;
    commit.current = null;
    if (!run) return;
    run();
    setAnim(null);
    setSettling(true);
    // 전환 없는 스타일이 한 번 그려진 뒤(다음 다음 프레임)에 전환을 되돌린다
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      frame.current = requestAnimationFrame(() => {
        frame.current = null;
        setSettling(false);
      });
    });
  }, []);

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current);
      if (frame.current !== null) cancelAnimationFrame(frame.current);
    },
    [],
  );

  const play = (next: Anim, apply: () => PhotoItem[]) => {
    if (prefersReducedMotion()) {
      onChange(apply());
      return;
    }
    commit.current = () => onChange(apply());
    // 되돌림 프레임을 기다리는 중에 다시 눌렀으면 전환을 바로 되살린다
    if (frame.current !== null) cancelAnimationFrame(frame.current);
    frame.current = null;
    setSettling(false);
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
  const remaining = MAX_PHOTOS - photos.length;

  // 확인한 뒤에야 빼는 애니메이션을 시작한다
  const remove = () => {
    const key = removingKey;
    setRemovingKey(null);
    if (key === null) return;
    play({ kind: "delete", key }, () => photos.filter((p) => p.key !== key));
  };

  // 언마운트되면 대기 중 변경은 버려지므로(폼도 함께 새로 만들어진다) 잠금만 풀어 준다.
  useEffect(() => {
    if (!locked) return;
    onBusyChange?.(true);
    return () => onBusyChange?.(false);
  }, [locked, onBusyChange]);

  // 이 사진이 지금 애니메이션에서 transform이 바뀌는 대상인지
  const animating = (i: number, key: string) =>
    (anim?.kind === "move" && (i === anim.from || i === anim.to)) ||
    (anim?.kind === "delete" && anim.key === key);

  const liStyle = (i: number, key: string) => {
    if (anim?.kind === "move" && (i === anim.from || i === anim.to)) {
      const dy = i === anim.from ? anim.fromDy : anim.toDy;
      return { transform: `translateY(${dy}px)` };
    }
    if (anim?.kind === "delete" && anim.key === key) {
      return { opacity: 0, transform: "scale(0.9)" };
    }
    return undefined;
  };

  return (
    <>
      <ul className="flex flex-col gap-3">
        {photos.map((photo, i) => {
          const pad = photo.fitMode === "PAD";
          // 여백 토글 중에는 바뀔 값을 미리 보여 준다
          const showPad =
            anim?.kind === "fit" && anim.key === photo.key ? !pad : pad;
          const imageClass = `aspect-square w-full bg-black transition-transform duration-200 ${
            showPad ? "object-contain" : "object-cover"
          } ${anim?.kind === "fit" && anim.key === photo.key ? "scale-95" : ""}`;
          return (
            <li
              key={photo.key}
              ref={(el) => {
                items.current[i] = el;
              }}
              className={
                settling
                  ? "transition-none"
                  : "transition-[transform,opacity] duration-200 ease-out"
              }
              style={liStyle(i, photo.key)}
              onTransitionEnd={(event) => {
                if (
                  event.target === event.currentTarget &&
                  event.propertyName === "transform" &&
                  animating(i, photo.key)
                ) {
                  finish();
                }
              }}
            >
              <div className="relative overflow-hidden rounded-md">
                {photo.kind === "saved" ? (
                  // eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL
                  <img
                    src={`${photo.url}?v=${version}`}
                    alt={`사진 ${i + 1}`}
                    className={imageClass}
                  />
                ) : (
                  <BlobImage
                    file={photo.file}
                    alt={`사진 ${i + 1}`}
                    className={imageClass}
                  />
                )}
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
                    onClick={() => setRemovingKey(photo.key)}
                  >
                    삭제
                  </button>
                  <button
                    type="button"
                    className={BUTTON}
                    disabled={locked}
                    onClick={() =>
                      play({ kind: "fit", key: photo.key }, () =>
                        photos.map((p) =>
                          p.key === photo.key
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
        {onAdd && remaining > 0 && (
          <li>
            <AddPhotoTile
              remaining={remaining}
              disabled={locked}
              onAdd={onAdd}
              onOverflow={(skipped) => onOverflow?.(skipped)}
            />
          </li>
        )}
      </ul>
      {removingKey !== null && (
        <ConfirmDialog
          title="이 사진을 뺄까요?"
          confirmLabel="빼기"
          onConfirm={remove}
          onClose={() => setRemovingKey(null)}
        />
      )}
    </>
  );
}
