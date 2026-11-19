"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";

// 네이티브 <dialog>를 모달로 연다. 폼 <form> 안에서 열려도 되게 body로 포탈하고,
// 안쪽 폼의 submit이 바깥 폼으로 올라가지 않게 막는다.
export function BottomSheet({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  // 누르기가 배경(<dialog> 자체)에서 시작됐는지. 패널에서 글을 끌어 선택하다 배경에서 놓아도 닫지 않는다.
  const pressedBackdrop = useRef(false);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    // 닫힐 때 돌아갈 곳. 열기 전에 포커스가 있던 버튼이다.
    const opener = document.activeElement;
    if (!dialog.open) dialog.showModal();
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
      // 패딩은 안쪽 래퍼에 둔다. <dialog> 자체에서 누르고 놓은 클릭만 배경이다.
      onPointerDown={(event) => {
        pressedBackdrop.current = event.target === event.currentTarget;
      }}
      onClick={(event) => {
        const fromBackdrop = pressedBackdrop.current;
        pressedBackdrop.current = false;
        if (fromBackdrop && event.target === event.currentTarget) onClose();
      }}
      onSubmit={(event) => event.stopPropagation()}
      className="fixed inset-x-0 bottom-0 m-0 max-h-[90vh] w-full max-w-none overflow-y-auto rounded-t-2xl border-t border-line bg-surface text-text backdrop:bg-black/60 motion-safe:animate-[sheet-up_200ms_ease-out]"
    >
      <div className="flex flex-col gap-3 p-5">
        <h2 id={titleId} className="font-semibold">
          {title}
        </h2>
        {children}
      </div>
    </dialog>,
    document.body,
  );
}
