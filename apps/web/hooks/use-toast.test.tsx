import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { Toast } from "@/components/ui/Toast";
import { TOAST_MS, useToast } from "@/hooks/use-toast";

const onAction = vi.fn();

function Harness() {
  const { toast, show, dismiss } = useToast();
  return (
    <>
      <button
        type="button"
        onClick={() => show("사진을 뺐어요", { label: "실행 취소", onAction })}
      >
        열기
      </button>
      <button type="button" onClick={() => show("다른 알림")}>
        다시
      </button>
      <Toast toast={toast} onDismiss={dismiss} />
    </>
  );
}

describe("useToast + Toast", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    onAction.mockClear();
  });
  afterEach(() => vi.useRealTimers());

  it("동작 버튼을 누르면 onAction 후 사라진다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "열기" }));
    expect(screen.getByRole("status")).toHaveTextContent("사진을 뺐어요");
    await user.click(screen.getByRole("button", { name: "실행 취소" }));
    expect(onAction).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("TOAST_MS 뒤 저절로 사라지고 새 show는 이전 것을 바꾼다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Harness />);
    await user.click(screen.getByRole("button", { name: "열기" }));
    await user.click(screen.getByRole("button", { name: "다시" }));
    expect(screen.getByRole("status")).toHaveTextContent("다른 알림");
    act(() => {
      vi.advanceTimersByTime(TOAST_MS);
    });
    expect(screen.getByRole("status")).toBeEmptyDOMElement();
  });

  it("알림_영역은_뜨기_전부터_있고_글만_바뀐다", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    render(<Harness />);
    const status = screen.getByRole("status");
    expect(status).toBeEmptyDOMElement();
    await user.click(screen.getByRole("button", { name: "열기" }));
    expect(screen.getByRole("status")).toBe(status);
    expect(status).toHaveTextContent("사진을 뺐어요");
  });
});
