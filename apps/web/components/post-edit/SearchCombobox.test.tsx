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
  selectedLabel = null,
  onClear = () => undefined,
}: {
  items: Item[];
  onPick: (item: Item) => void;
  selectedLabel?: string | null;
  onClear?: () => void;
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
      selectedLabel={selectedLabel}
      onClear={onClear}
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

  it("shows a chip instead of the search input when a value is selected", () => {
    render(<Harness items={MANY} onPick={vi.fn()} selectedLabel="서울장수" />);

    expect(
      screen.getByRole("button", { name: "서울장수" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "서울장수 지우기" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).toBeNull();
  });

  it("turns the chip into a focused search input on click", async () => {
    render(<Harness items={MANY} onPick={vi.fn()} selectedLabel="서울장수" />);

    await userEvent.click(screen.getByRole("button", { name: "서울장수" }));

    expect(screen.getByRole("combobox")).toHaveFocus();
  });

  it("calls onClear once and focuses the search input on the chip x", async () => {
    const onClear = vi.fn();
    render(
      <Harness
        items={MANY}
        onPick={vi.fn()}
        selectedLabel="서울장수"
        onClear={onClear}
      />,
    );

    await userEvent.click(
      screen.getByRole("button", { name: "서울장수 지우기" }),
    );

    expect(onClear).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("combobox")).toHaveFocus();
  });

  it("returns to the chip when focus leaves without picking", async () => {
    render(
      <>
        <Harness items={MANY} onPick={vi.fn()} selectedLabel="서울장수" />
        <button type="button">바깥</button>
      </>,
    );
    await userEvent.click(screen.getByRole("button", { name: "서울장수" }));

    await userEvent.click(screen.getByRole("button", { name: "바깥" }));

    expect(screen.queryByRole("combobox")).toBeNull();
    expect(
      screen.getByRole("button", { name: "서울장수" }),
    ).toBeInTheDocument();
  });

  it("closes the list on Escape", async () => {
    render(<Harness items={MANY} onPick={vi.fn()} />);
    await userEvent.type(screen.getByRole("combobox"), "술");
    expect(screen.getByRole("listbox")).toBeInTheDocument();

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("closes the list on a pointerdown outside", async () => {
    render(
      <>
        <Harness items={MANY} onPick={vi.fn()} />
        <button type="button">바깥</button>
      </>,
    );
    await userEvent.type(screen.getByRole("combobox"), "술");
    expect(screen.getByRole("listbox")).toBeInTheDocument();

    fireEvent.pointerDown(screen.getByRole("button", { name: "바깥" }));

    expect(screen.queryByRole("listbox")).toBeNull();
  });

  it("shows the chip again when a label appears after the x and a focus exit", async () => {
    function Wrapper() {
      const [label, setLabel] = useState<string | null>("서울장수");
      return (
        <>
          <Harness
            items={MANY}
            onPick={vi.fn()}
            selectedLabel={label}
            onClear={() => setLabel(null)}
          />
          <button type="button" onClick={() => setLabel("새 이름")}>
            바깥
          </button>
        </>
      );
    }
    render(<Wrapper />);
    await userEvent.click(
      screen.getByRole("button", { name: "서울장수 지우기" }),
    );

    await userEvent.click(screen.getByRole("button", { name: "바깥" }));

    expect(screen.getByRole("button", { name: "새 이름" })).toBeInTheDocument();
    expect(screen.queryByRole("combobox")).toBeNull();
  });

  it("returns to the chip on Escape when editing with an empty query", async () => {
    render(<Harness items={MANY} onPick={vi.fn()} selectedLabel="서울장수" />);
    await userEvent.click(screen.getByRole("button", { name: "서울장수" }));

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("combobox")).toBeNull();
    expect(
      screen.getByRole("button", { name: "서울장수" }),
    ).toBeInTheDocument();
  });

  it("고른_뒤_칩_버튼에_포커스가_간다", async () => {
    function Picking() {
      const [query, setQuery] = useState("");
      const [picked, setPicked] = useState<Item | null>(null);
      return (
        <SearchCombobox
          label="술"
          items={MANY}
          getKey={(item) => item.id}
          renderItem={(item) => item.name}
          selectedKey={picked?.id ?? null}
          onPick={setPicked}
          query={query}
          onQueryChange={setQuery}
          selectedLabel={picked?.name ?? null}
          onClear={() => setPicked(null)}
        />
      );
    }
    render(<Picking />);
    await userEvent.type(screen.getByRole("combobox", { name: "술" }), "술");
    await userEvent.keyboard("{ArrowDown}{Enter}");
    expect(screen.getByRole("button", { name: "막걸리" })).toHaveFocus();
  });

  it("탭으로_나가면_목록을_닫는다", async () => {
    render(
      <>
        <Harness items={MANY} onPick={vi.fn()} />
        <button type="button">다음</button>
      </>,
    );
    await userEvent.type(screen.getByRole("combobox", { name: "술" }), "술");
    expect(screen.getByRole("listbox")).toBeInTheDocument();
    await userEvent.tab();
    expect(screen.getByRole("button", { name: "다음" })).toHaveFocus();
    expect(screen.queryByRole("listbox")).toBeNull();
  });
});
