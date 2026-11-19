"use client";

import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import type { BreweryResponse } from "@/types/api/brewery";

type BrewerySheetProps = {
  brewery: BreweryResponse;
  onClose: () => void;
};

// 수정 화면 위에 네이티브 <dialog>를 모달로 연다(ConfirmDialog와 같은 방식).
// 수정 폼 <form> 안에서 열리므로 body로 포탈하고, 안쪽 폼의 submit이 바깥 폼으로 올라가지 않게 막는다.
export function BrewerySheet({ brewery, onClose }: BrewerySheetProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    // 닫힐 때 돌아갈 곳. 열기 전에 포커스가 있던 버튼이다.
    const opener = document.activeElement;
    if (!dialog.open) dialog.showModal();
    dialog.querySelector<HTMLElement>("input")?.focus();
    return () => {
      if (opener instanceof HTMLElement && opener.isConnected) opener.focus();
    };
  }, []);
  return createPortal(
    <dialog
      ref={ref}
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onSubmit={(event) => event.stopPropagation()}
      className="fixed inset-x-0 bottom-0 m-0 max-h-[90vh] w-full max-w-none overflow-y-auto rounded-t-lg backdrop:bg-black/40"
    >
      <div className="flex flex-col gap-3 p-5">
        <h2 id={titleId} className="font-semibold">
          양조장 정보
        </h2>
        <p className="text-sm text-neutral-700">
          이 양조장을 쓰는 다른 게시물에도 반영돼요
        </p>
        <BreweryForm brewery={brewery} onSaved={() => onClose()} />
        <button
          type="button"
          onClick={onClose}
          className="rounded-md border border-neutral-300 px-4 py-2.5 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        >
          닫기
        </button>
      </div>
    </dialog>,
    document.body,
  );
}
