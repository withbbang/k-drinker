"use client";

import {
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";

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
  inputClassName: string;
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
  inputClassName,
}: SearchComboboxProps<T>) {
  const id = useId();
  const listId = `${id}-list`;
  const [active, setActive] = useState(-1);
  // Safari는 조합 확정(compositionend) 뒤 같은 틱에 isComposing=false인 Enter를 보낸다. 그 Enter를 거른다.
  const justComposed = useRef(false);
  const open = query.trim() !== "" && items.length > 0;

  const pick = (item: T) => {
    onPick(item);
    onQueryChange("");
    setActive(-1);
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
    } else if (event.key === "Escape" && query !== "") {
      event.preventDefault();
      onQueryChange("");
      setActive(-1);
    }
  };

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={`${id}-input`} className="text-sm">
        {label}
      </label>
      <input
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
          onQueryChange(event.target.value);
        }}
        onKeyDown={handleKeyDown}
        onCompositionEnd={() => {
          justComposed.current = true;
          setTimeout(() => {
            justComposed.current = false;
          }, 0);
        }}
        className={inputClassName}
      />
      {open && (
        <ul id={listId} role="listbox" className="flex flex-col gap-1">
          {items.map((item, index) => {
            const selected = getKey(item) === selectedKey;
            return (
              <li
                key={getKey(item)}
                id={`${id}-opt-${index}`}
                role="option"
                aria-selected={selected}
                onClick={() => pick(item)}
                className={`cursor-pointer rounded-md border px-3 py-2 text-left text-sm ${
                  index === active
                    ? "border-neutral-900 bg-neutral-100"
                    : "border-neutral-300"
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
