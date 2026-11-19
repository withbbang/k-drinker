import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { PhotoStrip } from "@/components/ui/PhotoStrip";

type Item = { key: string; pad?: boolean };

const items = (n: number): Item[] =>
  Array.from({ length: n }, (_, i) => ({ key: `k${i + 1}` }));

function setup(
  list: Item[],
  props: Partial<React.ComponentProps<typeof PhotoStrip<Item>>> = {},
) {
  const handlers = {
    onReorder: vi.fn(),
    onRemove: vi.fn(),
    onOpen: vi.fn(),
    onAdd: vi.fn(),
  };
  render(
    <PhotoStrip<Item>
      items={list}
      getKey={(item) => item.key}
      renderImage={(item, className) => (
        // eslint-disable-next-line @next/next/no-img-element -- 테스트용
        <img alt="" src={`/${item.key}.jpg`} className={className} />
      )}
      max={10}
      {...handlers}
      {...props}
    />,
  );
  return handlers;
}

describe("PhotoStrip", () => {
  it("첫 장 이름에 대표가 붙는다", () => {
    setup(items(3));
    expect(
      screen.getByRole("button", { name: "1번째 사진, 대표" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "2번째 사진" }),
    ).toBeInTheDocument();
  });

  it("빼기 배지를 누르면 그 키로 onRemove를 부른다", () => {
    const { onRemove } = setup(items(3));
    fireEvent.click(screen.getByRole("button", { name: "2번째 사진 빼기" }));
    expect(onRemove).toHaveBeenCalledWith("k2");
  });

  it("한 장만 남으면 빼기 배지가 없다", () => {
    setup(items(1));
    expect(screen.queryByRole("button", { name: /빼기/ })).toBeNull();
  });

  it("썸네일을 누르면 onOpen을 부른다", () => {
    const { onOpen } = setup(items(3));
    fireEvent.click(screen.getByRole("button", { name: "3번째 사진" }));
    expect(onOpen).toHaveBeenCalledWith("k3");
  });

  it("max장이면 추가 타일이 없고, 모자라면 있다", () => {
    setup(items(10));
    expect(screen.queryByRole("button", { name: "사진 추가" })).toBeNull();
  });

  it("max보다 적으면 추가 타일이 있다", () => {
    setup(items(3));
    expect(
      screen.getByRole("button", { name: "사진 추가" }),
    ).toBeInTheDocument();
  });

  it("disabled면 빼기와 추가가 막힌다", () => {
    const { onRemove } = setup(items(3), { disabled: true });
    const remove = screen.getByRole("button", { name: "2번째 사진 빼기" });
    expect(remove).toBeDisabled();
    fireEvent.click(remove);
    expect(onRemove).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "사진 추가" })).toBeDisabled();
  });

  it("여백 항목에는 여백 있음 글자가 있다", () => {
    setup([{ key: "a" }, { key: "b", pad: true }], {
      isPadded: (item) => item.pad === true,
    });
    expect(screen.getAllByText("여백 있음")).toHaveLength(1);
  });
});
