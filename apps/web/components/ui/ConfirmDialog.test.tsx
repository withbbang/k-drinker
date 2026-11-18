import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";

function setup(pending = false) {
  const onConfirm = vi.fn();
  const onClose = vi.fn();
  render(
    <ConfirmDialog
      title="이 사진을 뺄까요?"
      confirmLabel="빼기"
      pending={pending}
      onConfirm={onConfirm}
      onClose={onClose}
    >
      <p>본문</p>
    </ConfirmDialog>,
  );
  return { onConfirm, onClose, dialog: screen.getByRole("dialog") };
}

describe("ConfirmDialog", () => {
  it("배경을_누르면_onClose", () => {
    const { dialog, onClose } = setup();

    fireEvent.click(dialog);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("내용을_누르면_닫히지_않는다", async () => {
    const { onClose } = setup();

    await userEvent.click(screen.getByText("이 사진을 뺄까요?"));
    await userEvent.click(screen.getByText("본문"));

    expect(onClose).not.toHaveBeenCalled();
  });

  it("Esc면_onClose", () => {
    const { dialog, onClose } = setup();

    fireEvent(dialog, new Event("cancel", { cancelable: true }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("진행_중이면_배경·Esc로_닫히지_않고_버튼이_비활성", () => {
    const { dialog, onClose } = setup(true);

    fireEvent.click(dialog);
    fireEvent(dialog, new Event("cancel", { cancelable: true }));

    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "빼기" })).toBeDisabled();
  });

  it("확인_버튼은_onConfirm을_부른다", async () => {
    const { onConfirm } = setup();

    await userEvent.click(screen.getByRole("button", { name: "빼기" }));

    expect(onConfirm).toHaveBeenCalledTimes(1);
  });
});
