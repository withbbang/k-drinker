import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { BottomSheet } from "@/components/ui/BottomSheet";

describe("BottomSheet", () => {
  it("제목이 이름인 dialog를 열고 Esc로 닫는다", () => {
    const onClose = vi.fn();
    render(
      <BottomSheet title="양조장" onClose={onClose}>
        <p>내용</p>
      </BottomSheet>,
    );
    const dialog = screen.getByRole("dialog", { name: "양조장" });
    fireEvent(dialog, new Event("cancel", { cancelable: true }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("안쪽 폼 submit이 바깥 폼으로 올라가지 않는다", () => {
    const outer = vi.fn();
    render(
      <form onSubmit={outer}>
        <BottomSheet title="양조장" onClose={vi.fn()}>
          <form aria-label="안쪽">
            <button type="submit">저장</button>
          </form>
        </BottomSheet>
      </form>,
    );
    fireEvent.submit(screen.getByRole("form", { name: "안쪽" }));
    expect(outer).not.toHaveBeenCalled();
  });
});
