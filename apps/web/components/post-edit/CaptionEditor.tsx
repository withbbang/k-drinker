"use client";

import { useLayoutEffect, useRef, useState } from "react";

import { AutoTextarea } from "@/components/ui/AutoTextarea";
import { buttonClass } from "@/components/ui/button";

export const PLACEHOLDER_BUTTONS = [
  "{술이름}",
  "{종류}",
  "{도수}",
  "{용량}",
  "{별점}",
  "{양조장}",
] as const;
export const MAX_CAPTION_LENGTH = 2200;

type Field = "head" | "tail";
type Tab = "edit" | "preview";

type CaptionEditorProps = {
  head: string;
  tail: string;
  onHeadChange: (value: string) => void;
  onTailChange: (value: string) => void;
  onFieldBlur: () => void;
  templated: boolean;
  preview: {
    caption: string | null;
    length: number;
    fetching: boolean;
    error: boolean;
  };
  disabled?: boolean;
};

const TAB =
  "min-h-11 px-4 text-sm font-medium text-muted focus-visible:outline-2 focus-visible:outline-accent aria-selected:text-accent";
const BARE =
  "min-h-0 rounded-none border-0 bg-transparent px-3 py-2.5 focus-visible:outline-offset-[-2px]";

export function CaptionEditor({
  head,
  tail,
  onHeadChange,
  onTailChange,
  onFieldBlur,
  templated,
  preview,
  disabled,
}: CaptionEditorProps) {
  const [tab, setTab] = useState<Tab>("edit");
  const headRef = useRef<HTMLTextAreaElement>(null);
  const tailRef = useRef<HTMLTextAreaElement>(null);
  const lastField = useRef<Field | null>(null);
  const pendingCursor = useRef<{ field: Field; pos: number } | null>(null);
  // 칸마다 마지막 선택 범위. 미리보기에 다녀오면 칸이 새로 그려져 커서가 사라지므로 기억해 둔다.
  const selection = useRef<
    Record<Field, { start: number; end: number } | null>
  >({ head: null, tail: null });
  const remember = (field: Field, el: HTMLTextAreaElement) => {
    selection.current[field] = {
      start: el.selectionStart,
      end: el.selectionEnd,
    };
  };
  const over = preview.length > MAX_CAPTION_LENGTH;

  // 부모가 새 값을 내려준 뒤에 커서를 넣은 글 뒤로 옮긴다.
  useLayoutEffect(() => {
    const pending = pendingCursor.current;
    if (!pending) return;
    pendingCursor.current = null;
    const el = (pending.field === "head" ? headRef : tailRef).current;
    el?.setSelectionRange(pending.pos, pending.pos);
    selection.current[pending.field] = {
      start: pending.pos,
      end: pending.pos,
    };
  }, [head, tail]);

  const insert = (name: string) => {
    // 아직 아무 칸도 누르지 않았으면 앞부분 끝에 붙인다.
    const field = lastField.current ?? "head";
    const untouched = lastField.current === null;
    const el = (field === "head" ? headRef : tailRef).current;
    const value = field === "head" ? head : tail;
    const saved = selection.current[field];
    // 칸에 포커스가 있으면 지금 선택을, 아니면 기억해 둔 선택을 쓴다.
    const live = el !== null && document.activeElement === el;
    const start = untouched
      ? value.length
      : live
        ? el.selectionStart
        : Math.min(saved?.start ?? value.length, value.length);
    const end = untouched
      ? value.length
      : live
        ? el.selectionEnd
        : Math.min(saved?.end ?? value.length, value.length);
    const next = value.slice(0, start) + name + value.slice(end);
    pendingCursor.current = { field, pos: start + name.length };
    (field === "head" ? onHeadChange : onTailChange)(next);
    el?.focus();
    el?.setSelectionRange(start + name.length, start + name.length);
  };

  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-medium text-text">인스타그램 글</h2>
      <div role="tablist" className="flex border-b border-line">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "edit"}
          onClick={() => setTab("edit")}
          className={TAB}
        >
          편집
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "preview"}
          onClick={() => setTab("preview")}
          className={TAB}
        >
          미리보기
        </button>
      </div>
      {tab === "edit" ? (
        <>
          <div className="overflow-hidden rounded-field border border-line bg-surface">
            <AutoTextarea
              ref={headRef}
              aria-label="인스타그램 글 앞부분"
              value={head}
              disabled={disabled}
              onFocus={() => (lastField.current = "head")}
              onSelect={(event) => remember("head", event.currentTarget)}
              onKeyUp={(event) => remember("head", event.currentTarget)}
              onBlur={(event) => {
                remember("head", event.currentTarget);
                onFieldBlur();
              }}
              onChange={(event) => onHeadChange(event.target.value)}
              className={BARE}
            />
            <p className="border-y border-line px-3 py-2 text-sm text-muted">
              양조장 정보는 자동으로 채워져요
            </p>
            <AutoTextarea
              ref={tailRef}
              aria-label="인스타그램 글 뒷부분"
              value={tail}
              disabled={disabled}
              onFocus={() => (lastField.current = "tail")}
              onSelect={(event) => remember("tail", event.currentTarget)}
              onKeyUp={(event) => remember("tail", event.currentTarget)}
              onBlur={(event) => {
                remember("tail", event.currentTarget);
                onFieldBlur();
              }}
              onChange={(event) => onTailChange(event.target.value)}
              className={BARE}
            />
          </div>
          {templated && (
            <div className="flex flex-wrap gap-2">
              {PLACEHOLDER_BUTTONS.map((name) => {
                const label = name.slice(1, -1);
                return (
                  <button
                    key={name}
                    type="button"
                    aria-label={`${label} 넣기`}
                    disabled={disabled}
                    onClick={() => insert(name)}
                    className={buttonClass(
                      "secondary",
                      "min-h-9 px-3 font-medium",
                    )}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          )}
        </>
      ) : preview.error ? (
        <p role="alert" className="text-sm text-danger">
          미리보기를 불러오지 못했어요
        </p>
      ) : (
        <pre
          className={`whitespace-pre-wrap rounded-field border border-line bg-surface p-3 font-sans text-sm text-text ${preview.fetching ? "opacity-50" : ""}`}
        >
          {preview.caption}
        </pre>
      )}
      <p className={`text-sm ${over ? "text-danger" : "text-muted"}`}>
        {preview.length.toLocaleString("ko-KR")}자 / 2,200자
      </p>
      {over && (
        <p role="alert" className="text-sm text-danger">
          2,200자를 넘었어요
        </p>
      )}
    </section>
  );
}
