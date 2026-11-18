import { useState } from "react";

import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { SearchCombobox } from "@/components/post-edit/SearchCombobox";

type Item = { id: number; name: string };

const MANY: Item[] = [
  { id: 1, name: "막걸리" },
  { id: 2, name: "약주" },
  { id: 3, name: "소주" },
];

// 검색 결과는 검색어와 따로 늦게 도착한다. items를 바꿔 다시 그리면 그 상황이 된다.
function Harness({
  items,
  onPick,
}: {
  items: Item[];
  onPick: (item: Item) => void;
}) {
  const [query, setQuery] = useState("");
  return (
    <SearchCombobox
      label="술"
      items={items}
      getKey={(item) => item.id}
      renderItem={(item) => item.name}
      selectedKey={null}
      onPick={onPick}
      query={query}
      onQueryChange={setQuery}
      inputClassName=""
    />
  );
}

describe("SearchCombobox", () => {
  it("does not pick an item when the active index is out of range", async () => {
    // given — 세 번째 항목을 가리킨 뒤 늦게 온 결과가 1개로 줄었다
    const onPick = vi.fn();
    const { rerender } = render(<Harness items={MANY} onPick={onPick} />);
    await userEvent.type(screen.getByRole("combobox", { name: "술" }), "술");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}{ArrowDown}");
    rerender(<Harness items={MANY.slice(0, 1)} onPick={onPick} />);

    // when
    await userEvent.keyboard("{Enter}");

    // then
    expect(onPick).not.toHaveBeenCalled();
  });

  it("picks the first item on Enter when nothing is active", async () => {
    // given
    const onPick = vi.fn();
    render(<Harness items={MANY} onPick={onPick} />);
    await userEvent.type(screen.getByRole("combobox", { name: "술" }), "술");

    // when
    await userEvent.keyboard("{Enter}");

    // then
    expect(onPick).toHaveBeenCalledWith(MANY[0]);
  });

  it("ignores Enter reported with the IME keyCode 229", async () => {
    // given
    const onPick = vi.fn();
    render(<Harness items={MANY} onPick={onPick} />);
    const input = screen.getByRole("combobox", { name: "술" });
    await userEvent.type(input, "술");

    // when
    fireEvent.keyDown(input, { key: "Enter", keyCode: 229 });

    // then
    expect(onPick).not.toHaveBeenCalled();
  });

  it("ignores the Enter that Safari sends right after composition ends", async () => {
    // given
    const onPick = vi.fn();
    render(<Harness items={MANY} onPick={onPick} />);
    const input = screen.getByRole("combobox", { name: "술" });
    await userEvent.type(input, "술");

    // when — 조합 확정 뒤 같은 틱에 isComposing=false Enter가 온다
    fireEvent.compositionEnd(input);
    const notPrevented = fireEvent.keyDown(input, {
      key: "Enter",
      keyCode: 13,
    });

    // then — 고르지 않고, 폼 제출도 막는다
    expect(onPick).not.toHaveBeenCalled();
    expect(notPrevented).toBe(false);
  });

  it("picks on a later Enter after composition ends", async () => {
    // given
    const onPick = vi.fn();
    render(<Harness items={MANY} onPick={onPick} />);
    const input = screen.getByRole("combobox", { name: "술" });
    await userEvent.type(input, "술");
    fireEvent.compositionEnd(input);
    await new Promise((r) => setTimeout(r, 0));

    // when
    fireEvent.keyDown(input, { key: "Enter", keyCode: 13 });

    // then
    expect(onPick).toHaveBeenCalledWith(MANY[0]);
  });
});
