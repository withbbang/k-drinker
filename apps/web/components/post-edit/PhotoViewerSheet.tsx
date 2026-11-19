"use client";

import type { ReactNode } from "react";

import { BottomSheet } from "@/components/ui/BottomSheet";
import { buttonClass } from "@/components/ui/button";

// 사진을 크게 보여 주고 여백을 넣고 빼는 시트
export function PhotoViewerSheet({
  title,
  image,
  padded,
  onTogglePad,
  onClose,
  disabled = false,
}: {
  title: string;
  image: ReactNode;
  padded: boolean;
  onTogglePad: () => void;
  onClose: () => void;
  disabled?: boolean;
}) {
  return (
    <BottomSheet title={title} onClose={onClose}>
      <div className="flex justify-center">{image}</div>
      <button
        type="button"
        disabled={disabled}
        onClick={onTogglePad}
        className={buttonClass("secondary")}
      >
        {padded ? "여백 빼기" : "여백 넣기"}
      </button>
    </BottomSheet>
  );
}
