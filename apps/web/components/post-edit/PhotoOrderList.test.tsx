import { useState } from "react";

import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { PhotoOrderList } from "@/components/post-edit/PhotoOrderList";
import { fromSaved, type PhotoItem } from "@/components/post-edit/photo-items";

function make(count: number): PhotoItem[] {
  return fromSaved(
    Array.from({ length: count }, (_, i) => ({
      id: i + 1,
      position: i,
      url: `/media/${i + 1}.jpg`,
      fitMode: "CROP" as const,
    })),
  );
}

function Harness({ initial }: { initial: PhotoItem[] }) {
  const [photos, setPhotos] = useState(initial);
  return (
    <>
      <PhotoOrderList photos={photos} version={3} onChange={setPhotos} />
      <output data-testid="order">
        {photos
          .map((p) => `${p.kind === "saved" ? p.id : p.key}:${p.fitMode}`)
          .join(",")}
      </output>
    </>
  );
}

function mockReducedMotion(reduce: boolean) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: reduce && query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("PhotoOrderList", () => {
  it("moves a photo up", async () => {
    mockReducedMotion(true);
    render(<Harness initial={make(3)} />);
    await userEvent.click(screen.getAllByRole("button", { name: "위로" })[1]);
    expect(screen.getByTestId("order")).toHaveTextContent(
      "2:CROP,1:CROP,3:CROP",
    );
  });

  it("cannot delete the last photo", async () => {
    mockReducedMotion(true);
    render(<Harness initial={make(2)} />);
    await userEvent.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    await userEvent.click(screen.getByRole("button", { name: "빼기" }));
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP");
    expect(screen.getByRole("button", { name: "삭제" })).toBeDisabled();
  });

  it("toggles padding", async () => {
    mockReducedMotion(true);
    render(<Harness initial={make(2)} />);
    await userEvent.click(
      screen.getAllByRole("button", { name: "여백 넣기" })[1],
    );
    expect(screen.getByTestId("order")).toHaveTextContent("1:CROP,2:PAD");
    expect(screen.getByRole("button", { name: "여백 빼기" })).toBeVisible();
  });

  it("puts the four buttons in an overlay beside the image", () => {
    render(<Harness initial={make(2)} />);
    const item = screen.getAllByRole("listitem")[0];
    const box = screen.getByAltText("사진 1").parentElement as HTMLElement;
    expect(box).toHaveClass("relative");
    for (const name of ["위로", "아래로", "삭제", "여백 넣기"]) {
      const button = within(item).getByRole("button", { name });
      expect(box.contains(button)).toBe(true);
    }
    expect(within(box).getAllByRole("button")).toHaveLength(4);
  });

  it("locks buttons during the move animation, then applies it", () => {
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(3)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "아래로" })[0]);
    for (const b of screen.getAllByRole("button")) expect(b).toBeDisabled();
    expect(screen.getByTestId("order")).toHaveTextContent(
      "1:CROP,2:CROP,3:CROP",
    );
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(screen.getByTestId("order")).toHaveTextContent(
      "2:CROP,1:CROP,3:CROP",
    );
    expect(screen.getAllByRole("button", { name: "위로" })[1]).toBeEnabled();
  });

  it("finishes the move on transitionend", () => {
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(2)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "아래로" })[0]);
    fireEvent.transitionEnd(screen.getAllByRole("listitem")[0], {
      propertyName: "transform",
    });
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP,1:CROP");
  });

  it("ignores transitionend of other properties and other items", () => {
    // given
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(3)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "아래로" })[0]);

    // when — 투명도 전환과, 움직이지 않는 세 번째 사진의 전환
    const items = screen.getAllByRole("listitem");
    fireEvent.transitionEnd(items[0], { propertyName: "opacity" });
    fireEvent.transitionEnd(items[2], { propertyName: "transform" });

    // then
    expect(screen.getByTestId("order")).toHaveTextContent(
      "1:CROP,2:CROP,3:CROP",
    );
  });

  it("resets transform without transition on the commit render", () => {
    // given
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(2)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "아래로" })[0]);

    // when
    act(() => {
      vi.advanceTimersByTime(250);
    });

    // then — 순서가 바뀐 렌더에서는 되돌아가는 전환이 없다
    for (const li of screen.getAllByRole("listitem")) {
      expect(li).toHaveClass("transition-none");
      expect(li.style.transform).toBe("");
    }

    // when — 다음 프레임
    act(() => {
      vi.advanceTimersByTime(100);
    });

    // then — 전환을 되돌린다
    for (const li of screen.getAllByRole("listitem")) {
      expect(li).not.toHaveClass("transition-none");
    }
  });

  it("delays delete and padding toggle too", () => {
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(2)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    fireEvent.click(screen.getByRole("button", { name: "빼기" }));
    expect(screen.getByTestId("order")).toHaveTextContent("1:CROP,2:CROP");
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP");

    fireEvent.click(screen.getByRole("button", { name: "여백 넣기" }));
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP");
    act(() => {
      vi.advanceTimersByTime(250);
    });
    expect(screen.getByTestId("order")).toHaveTextContent("2:PAD");
  });

  it("applies immediately under reduced motion", () => {
    vi.useFakeTimers();
    mockReducedMotion(true);
    render(<Harness initial={make(2)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "아래로" })[0]);
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP,1:CROP");
    expect(screen.getAllByRole("button", { name: "위로" })[1]).toBeEnabled();
  });

  it("삭제는_확인해야_한다", async () => {
    // given
    mockReducedMotion(true);
    const onChange = vi.fn();
    const photos = make(3);
    render(<PhotoOrderList photos={photos} version={3} onChange={onChange} />);

    // when — 취소하면
    await userEvent.click(screen.getAllByRole("button", { name: "삭제" })[1]);
    expect(screen.getByText("이 사진을 뺄까요?")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "취소" }));

    // then
    expect(onChange).not.toHaveBeenCalled();
    expect(screen.queryByText("이 사진을 뺄까요?")).toBeNull();

    // when — 빼기를 누르면
    await userEvent.click(screen.getAllByRole("button", { name: "삭제" })[1]);
    await userEvent.click(screen.getByRole("button", { name: "빼기" }));

    // then
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith([photos[0], photos[2]]);
  });

  it("shows new photos from their files and appends added ones", async () => {
    // given
    URL.createObjectURL = vi.fn(() => "blob:new");
    URL.revokeObjectURL = vi.fn();
    const onAdd = vi.fn();
    const added = new File(["x"], "a.jpg", { type: "image/jpeg" });
    const photos: PhotoItem[] = [
      ...make(1),
      { key: "new-0", kind: "new", file: added, fitMode: "CROP" },
    ];
    render(
      <PhotoOrderList
        photos={photos}
        version={3}
        onChange={vi.fn()}
        onAdd={onAdd}
        onOverflow={vi.fn()}
      />,
    );
    expect(screen.getByAltText("사진 2")).toHaveAttribute("src", "blob:new");

    // when
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]')!;
    await userEvent.upload(input, added);

    // then
    expect(onAdd).toHaveBeenCalledWith([added]);
  });

  it("disables every photo control while disabled", () => {
    // given / when
    render(
      <PhotoOrderList
        photos={make(3)}
        version={3}
        disabled
        onChange={vi.fn()}
        onAdd={vi.fn()}
        onOverflow={vi.fn()}
      />,
    );

    // then
    for (const name of ["위로", "아래로", "삭제", "여백 넣기"]) {
      for (const button of screen.getAllByRole("button", { name })) {
        expect(button).toBeDisabled();
      }
    }
    expect(screen.getByRole("button", { name: "사진 추가" })).toBeDisabled();
  });

  it("hides the add tile at 10 photos", () => {
    render(
      <PhotoOrderList
        photos={make(10)}
        version={3}
        onChange={vi.fn()}
        onAdd={vi.fn()}
        onOverflow={vi.fn()}
      />,
    );
    expect(screen.queryByRole("button", { name: "사진 추가" })).toBeNull();
  });

  it("busts the image cache with the version", () => {
    render(<Harness initial={make(1)} />);
    expect(screen.getByAltText("사진 1")).toHaveAttribute(
      "src",
      "/media/1.jpg?v=3",
    );
  });
});
