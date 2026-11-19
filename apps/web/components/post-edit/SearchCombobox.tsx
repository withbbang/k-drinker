"use client";

import {
  useEffect,
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

import { Chip } from "@/components/ui/Chip";

type SearchComboboxProps<T> = {
  label: string;
  items: T[];
  getKey: (item: T) => string | number;
  renderItem: (item: T) => ReactNode;
  // 지금 선택돼 있는 항목의 키. 목록에서 aria-selected로 표시한다.
  selectedKey: string | number | null;
  onPick: (item: T) => void;
  query: string;
  onQueryChange: (query: string) => void;
  // 지금 고른 값의 이름. 있고 편집 중이 아니면 검색칸 자리에 칩으로 보인다.
  selectedLabel: string | null;
  // 칩의 × 를 눌렀을 때. 호출한 뒤 검색칸으로 바뀌고 포커스가 간다.
  onClear: () => void;
  // 포커스가 이 컴포넌트 밖으로 나갔을 때.
  onBlurField?: () => void;
};

// 검색 입력 + 결과 목록. 폼 안에 있어도 Enter가 폼을 제출하지 않고 항목을 고른다.
export function SearchCombobox<T>({
  label,
  items,
  getKey,
  renderItem,
  selectedKey,
  onPick,
  query,
  onQueryChange,
  selectedLabel,
  onClear,
  onBlurField,
}: SearchComboboxProps<T>) {
  const id = useId();
  const listId = `${id}-list`;
  const [active, setActive] = useState(-1);
  // Safari는 조합 확정(compositionend) 뒤 같은 틱에 isComposing=false인 Enter를 보낸다. 그 Enter를 거른다.
  const justComposed = useRef(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  // 칩에서 검색칸으로 바뀐 상태.
  const [editing, setEditing] = useState(false);
  // 바깥을 누르거나 Esc로 닫은 목록. 검색어가 바뀌면 다시 열린다.
  const [dismissed, setDismissed] = useState(false);
  const open = !dismissed && query.trim() !== "" && items.length > 0;
  const showChip = selectedLabel !== null && !editing;

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  useEffect(() => {
    if (!open) return;
    const close = (event: PointerEvent) => {
      if (!wrapperRef.current?.contains(event.target as Node)) {
        setDismissed(true);
      }
    };
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, [open]);

  const pick = (item: T) => {
    onPick(item);
    onQueryChange("");
    setActive(-1);
    setEditing(false);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      // IME 조합 중 Enter는 글자 확정이므로 건드리지 않는다. 폼 제출은 항상 막는다.
      if (event.nativeEvent.isComposing || event.keyCode === 229) return;
      event.preventDefault();
      if (justComposed.current) return;
      // 결과가 줄어 가리키던 항목이 사라졌으면 고르지 않는다.
      const index = active >= 0 ? active : 0;
      if (open && index < items.length) pick(items[index]);
    } else if (event.key === "ArrowDown" && open) {
      event.preventDefault();
      setActive((current) => (current + 1) % items.length);
    } else if (event.key === "ArrowUp" && open) {
      event.preventDefault();
      setActive((current) => (current <= 0 ? items.length - 1 : current - 1));
    } else if (event.key === "Escape" && (open || query !== "")) {
      event.preventDefault();
      setDismissed(true);
      onQueryChange("");
      setActive(-1);
    }
  };

  return (
    <div
      ref={wrapperRef}
      className="relative flex flex-col gap-1"
      onBlur={(event) => {
        if (wrapperRef.current?.contains(event.relatedTarget)) return;
        if (selectedLabel !== null) {
          setEditing(false);
          onQueryChange("");
        }
        setActive(-1);
        onBlurField?.();
      }}
    >
      {showChip ? (
        <div>
          <Chip
            label={selectedLabel}
            onActivate={() => setEditing(true)}
            onClear={() => {
              onClear();
              setEditing(true);
            }}
          />
        </div>
      ) : (
        <>
          <label htmlFor={`${id}-input`} className="sr-only">
            {label}
          </label>
          <input
            ref={inputRef}
            id={`${id}-input`}
            type="search"
            role="combobox"
            aria-expanded={open}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={
              open && active >= 0 ? `${id}-opt-${active}` : undefined
            }
            value={query}
            onChange={(event) => {
              setActive(-1);
              setDismissed(false);
              onQueryChange(event.target.value);
            }}
            onKeyDown={handleKeyDown}
            onCompositionEnd={() => {
              justComposed.current = true;
              setTimeout(() => {
                justComposed.current = false;
              }, 0);
            }}
            className="min-h-11 w-full rounded-field border border-line bg-surface px-3 text-text placeholder:text-muted focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
          />
        </>
      )}
      {open && !showChip && (
        <ul
          id={listId}
          role="listbox"
          // 눌러도 입력칸이 포커스를 잃지 않게 해 클릭이 항목에 닿는다.
          onMouseDown={(event) => event.preventDefault()}
          className="absolute top-full z-20 mt-1 flex max-h-64 w-full flex-col overflow-auto rounded-field border border-line bg-surface py-1"
        >
          {items.map((item, index) => {
            const selected = getKey(item) === selectedKey;
            return (
              <li
                key={getKey(item)}
                id={`${id}-opt-${index}`}
                role="option"
                aria-selected={selected}
                onClick={() => pick(item)}
                className={`min-h-11 cursor-pointer px-3 py-2.5 text-left text-sm text-text ${
                  index === active ? "bg-line" : ""
                } ${selected ? "font-semibold" : ""}`}
              >
                {renderItem(item)}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
