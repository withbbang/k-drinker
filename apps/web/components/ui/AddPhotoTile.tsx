"use client";

import { useRef } from "react";

const ACCEPTED_TYPES = ["image/jpeg", "image/png"];

export function overflowMessage(skipped: number): string {
  return `사진은 10장까지 넣을 수 있어 ${skipped}장은 넣지 않았어요.`;
}

// 사진 추가 [+] 타일. 남은 자리(remaining)만큼만 받고 넘치면 알린다.
export function AddPhotoTile({
  remaining,
  onAdd,
  onOverflow,
  disabled,
}: {
  remaining: number;
  onAdd: (files: File[]) => void;
  onOverflow: (skipped: number) => void;
  disabled?: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  if (remaining <= 0) return null;

  return (
    <>
      <button
        type="button"
        aria-label="사진 추가"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        className="flex aspect-square min-h-11 min-w-11 items-center justify-center rounded-md border border-dashed border-line text-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-40"
      >
        +
      </button>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png"
        multiple
        hidden
        onChange={(event) => {
          // 서버는 JPEG·PNG만 읽을 수 있어 다른 형식은 걸러 낸다.
          const picked = Array.from(event.target.files ?? []).filter((f) =>
            ACCEPTED_TYPES.includes(f.type),
          );
          event.target.value = "";
          if (picked.length === 0) return;
          onAdd(picked.slice(0, remaining));
          if (picked.length > remaining) onOverflow(picked.length - remaining);
        }}
      />
    </>
  );
}
