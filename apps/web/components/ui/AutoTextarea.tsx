"use client";

import { useEffect, useRef, type ComponentProps } from "react";

// field-sizing을 지원하면 CSS만으로 늘어나고, 아니면 input 때 높이를 scrollHeight에 맞춘다.
export function AutoTextarea({
  className = "",
  onInput,
  ref: outerRef,
  ...props
}: ComponentProps<"textarea">) {
  const ref = useRef<HTMLTextAreaElement>(null);
  const native = () =>
    typeof CSS !== "undefined" &&
    typeof CSS.supports === "function" &&
    CSS.supports("field-sizing", "content");
  const fit = () => {
    const el = ref.current;
    if (!el || native()) return;
    el.style.height = "auto";
    el.style.height = `${el.scrollHeight}px`;
  };
  // 초기값·바깥에서 바뀐 값에도 높이를 맞춘다.
  useEffect(fit, [props.value]);
  return (
    <textarea
      rows={3}
      {...props}
      ref={(node) => {
        ref.current = node;
        if (typeof outerRef === "function") outerRef(node);
        else if (outerRef) outerRef.current = node;
      }}
      onInput={(event) => {
        fit();
        onInput?.(event);
      }}
      className={`field-sizing-content w-full resize-none rounded-field border border-line bg-surface px-3 py-2.5 text-text placeholder:text-muted focus-visible:outline-2 focus-visible:outline-accent ${className}`}
    />
  );
}
