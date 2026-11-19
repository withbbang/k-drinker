import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import { createElement, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useCaptionPreview } from "@/hooks/use-caption-preview";
import type { CaptionPreviewRequest } from "@/types/api/post";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));
vi.mock("@/lib/api", () => ({ apiFetch: apiFetchMock }));

const base: CaptionPreviewRequest = {
  drinkId: 1,
  newDrinkName: null,
  breweryId: null,
  rating: null,
  captionHead: "",
  captionTail: "",
};

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient();
  return createElement(QueryClientProvider, { client }, children);
}

describe("useCaptionPreview", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    apiFetchMock.mockReset();
    apiFetchMock.mockResolvedValue({
      caption: "글",
      length: 1,
      templated: true,
    });
  });
  afterEach(() => vi.useRealTimers());

  it("빠르게_세_번_바꾸면_마지막_값으로_한_번만_요청한다", async () => {
    const { result, rerender } = renderHook(
      ({ head }) => useCaptionPreview("7", { ...base, captionHead: head }),
      { wrapper, initialProps: { head: "a" } },
    );
    rerender({ head: "ab" });
    await act(() => vi.advanceTimersByTimeAsync(200));
    rerender({ head: "abc" });
    await act(() => vi.advanceTimersByTimeAsync(399));
    // 처음 값으로 한 번만 불러 둔 상태. 중간 값(ab)은 요청하지 않는다.
    expect(apiFetchMock).toHaveBeenCalledTimes(1);

    await act(() => vi.advanceTimersByTimeAsync(1));
    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(apiFetchMock).toHaveBeenCalledTimes(2);

    expect(result.current.data?.length).toBe(1);
    const [path, init] = apiFetchMock.mock.calls[1];
    expect(path).toBe("/api/v1/posts/7/caption-preview");
    expect(JSON.parse(init.body).captionHead).toBe("abc");
  });
});
