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
