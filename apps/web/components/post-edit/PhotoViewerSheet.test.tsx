import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { PhotoViewerSheet } from "@/components/post-edit/PhotoViewerSheet";

function setup(padded: boolean, disabled = false) {
  const onTogglePad = vi.fn();
  render(
    <PhotoViewerSheet
      title="2번째 사진"
      image={<p>큰 사진</p>}
      padded={padded}
      onTogglePad={onTogglePad}
      onClose={vi.fn()}
      disabled={disabled}
    />,
  );
  return onTogglePad;
}

describe("PhotoViewerSheet", () => {
  it("여백이 없으면 여백 넣기를 누를 수 있다", () => {
    const onTogglePad = setup(false);
    fireEvent.click(screen.getByRole("button", { name: "여백 넣기" }));
    expect(onTogglePad).toHaveBeenCalledTimes(1);
  });

  it("여백이 있으면 여백 빼기가 보인다", () => {
    setup(true);
    expect(
      screen.getByRole("button", { name: "여백 빼기" }),
    ).toBeInTheDocument();
  });

  it("disabled면 버튼이 막힌다", () => {
    setup(false, true);
    expect(screen.getByRole("button", { name: "여백 넣기" })).toBeDisabled();
  });
});
