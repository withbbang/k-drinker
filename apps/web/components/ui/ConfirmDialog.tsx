"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

type ConfirmDialogProps = {
  title: string;
  confirmLabel: string;
  cancelLabel?: string;
  pending?: boolean;
  children?: ReactNode;
  onConfirm: () => void;
  onClose: () => void;
};

// 네이티브 <dialog>를 모달로 연다. Esc·배경 클릭은 진행 중이 아닐 때만 onClose로 닫는다.
export function ConfirmDialog({
  title,
  confirmLabel,
  cancelLabel = "취소",
  pending = false,
  children,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);
  return (
    <dialog
      ref={ref}
      onCancel={(event) => {
        event.preventDefault();
        if (!pending) onClose();
      }}
      // 패딩은 안쪽 래퍼에 둔다. <dialog> 자체를 누른 클릭만 배경이다.
      onClick={(event) => {
        if (event.target === event.currentTarget && !pending) onClose();
      }}
      aria-labelledby={titleId}
      className="m-auto w-[min(90vw,24rem)] rounded-lg backdrop:bg-black/40"
    >
      <div className="p-5">
        <h2 id={titleId} className="mb-3 font-semibold">
          {title}
        </h2>
        {children}
        <div className="mt-4 flex gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={pending}
            className="flex-1 rounded-md border border-neutral-300 px-4 py-2.5 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={pending}
            className="flex-1 rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </dialog>
  );
}
