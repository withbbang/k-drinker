import { useState } from "react";

import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { PhotoOrderList } from "@/components/post-edit/PhotoOrderList";
import type { PostPhotoResponse } from "@/types/api/post";

function make(count: number): PostPhotoResponse[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    position: i,
    url: `/media/${i + 1}.jpg`,
    fitMode: "CROP" as const,
  }));
}

function Harness({ initial }: { initial: PostPhotoResponse[] }) {
  const [photos, setPhotos] = useState(initial);
  return (
    <>
      <PhotoOrderList photos={photos} version={3} onChange={setPhotos} />
      <output data-testid="order">
        {photos.map((p) => `${p.id}:${p.fitMode}`).join(",")}
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
    fireEvent.transitionEnd(screen.getAllByRole("listitem")[0]);
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP,1:CROP");
  });

  it("delays delete and padding toggle too", () => {
    vi.useFakeTimers();
    mockReducedMotion(false);
    render(<Harness initial={make(2)} />);
    fireEvent.click(screen.getAllByRole("button", { name: "삭제" })[0]);
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

  it("busts the image cache with the version", () => {
    render(<Harness initial={make(1)} />);
    expect(screen.getByAltText("사진 1")).toHaveAttribute(
      "src",
      "/media/1.jpg?v=3",
    );
  });
});
