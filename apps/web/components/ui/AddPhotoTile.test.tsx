import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { AddPhotoTile, overflowMessage } from "@/components/ui/AddPhotoTile";

const file = (name: string) => new File(["x"], name, { type: "image/jpeg" });

describe("AddPhotoTile", () => {
  it("remaining=1에 3장 고르면 onAdd 1장, onOverflow(2)", async () => {
    const onAdd = vi.fn();
    const onOverflow = vi.fn();
    const { container } = render(
      <AddPhotoTile remaining={1} onAdd={onAdd} onOverflow={onOverflow} />,
    );
    const files = [file("a.jpg"), file("b.jpg"), file("c.jpg")];

    await userEvent.upload(
      container.querySelector("input[type=file]") as HTMLInputElement,
      files,
    );

    expect(onAdd).toHaveBeenCalledWith([files[0]]);
    expect(onOverflow).toHaveBeenCalledWith(2);
  });

  it("accepts only jpeg and png and drops other formats", async () => {
    const onAdd = vi.fn();
    const onOverflow = vi.fn();
    const { container } = render(
      <AddPhotoTile remaining={3} onAdd={onAdd} onOverflow={onOverflow} />,
    );
    const input = container.querySelector(
      "input[type=file]",
    ) as HTMLInputElement;
    const webp = new File(["x"], "a.webp", { type: "image/webp" });
    const jpeg = file("b.jpg");

    // 선택기 단계 필터를 건너뛰고 직접 고른 경우도 걸러야 한다.
    await userEvent.upload(input, [webp, jpeg], { applyAccept: false });

    expect(input).toHaveAttribute("accept", "image/jpeg,image/png");
    expect(onAdd).toHaveBeenCalledWith([jpeg]);
    expect(onOverflow).not.toHaveBeenCalled();
  });

  it("remaining=0이면 사진 추가 버튼 없음", () => {
    render(<AddPhotoTile remaining={0} onAdd={vi.fn()} onOverflow={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "사진 추가" })).toBeNull();
  });

  it("overflowMessage 문구", () => {
    expect(overflowMessage(2)).toBe(
      "사진은 10장까지 넣을 수 있어 2장은 넣지 않았습니다.",
    );
  });
});
