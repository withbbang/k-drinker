import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { CaptionEditor } from "@/components/post-edit/CaptionEditor";

function setup(over: Partial<Parameters<typeof CaptionEditor>[0]> = {}) {
  const props = {
    head: "좋은 술",
    tail: "또 마실래요",
    onHeadChange: vi.fn(),
    onTailChange: vi.fn(),
    onFieldBlur: vi.fn(),
    templated: true,
    preview: {
      caption: "첫 줄\n둘째 줄",
      length: 10,
      fetching: false,
      error: false,
    },
    ...over,
  };
  render(<CaptionEditor {...props} />);
  return props;
}

describe("CaptionEditor", () => {
  it("미리보기 탭에서 줄바꿈을 그대로 보여 준다", async () => {
    setup();
    expect(
      screen.getByRole("textbox", { name: "인스타그램 글 앞부분" }),
    ).toBeVisible();
    await userEvent.click(screen.getByRole("tab", { name: "미리보기" }));
    expect(screen.getByText(/첫 줄/)).toHaveClass("whitespace-pre-wrap");
  });

  it("개발용 이름 없이 고정 안내만 보인다", () => {
    setup();
    expect(screen.queryByText(/캡션 머리|캡션 꼬리|양조장 블록/)).toBeNull();
    expect(
      screen.getByText("양조장 정보는 자동으로 채워져요"),
    ).toBeInTheDocument();
  });

  it("커서 자리에 이름표를 넣는다", async () => {
    const props = setup({ head: "좋은 술" });
    const head = screen.getByRole("textbox", { name: "인스타그램 글 앞부분" });
    await userEvent.click(head);
    (head as HTMLTextAreaElement).setSelectionRange(3, 3);
    await userEvent.click(screen.getByRole("button", { name: "술이름 넣기" }));
    expect(props.onHeadChange).toHaveBeenCalledWith("좋은 {술이름}술");
  });

  it("아무 칸도 누르지 않으면 앞부분 끝에 넣고 포커스한다", async () => {
    const props = setup();
    await userEvent.click(screen.getByRole("button", { name: "도수 넣기" }));
    expect(props.onHeadChange).toHaveBeenCalledWith("좋은 술{도수}");
    expect(
      screen.getByRole("textbox", { name: "인스타그램 글 앞부분" }),
    ).toHaveFocus();
  });

  it("뒷부분 칸에 포커스한 뒤에는 뒷부분에 넣는다", async () => {
    const props = setup();
    await userEvent.click(
      screen.getByRole("textbox", { name: "인스타그램 글 뒷부분" }),
    );
    await userEvent.click(screen.getByRole("button", { name: "별점 넣기" }));
    expect(props.onTailChange).toHaveBeenCalled();
    expect(props.onHeadChange).not.toHaveBeenCalled();
  });

  it("이름표 캡션이 아니면 넣기 버튼이 없다", () => {
    setup({ templated: false });
    expect(screen.queryByRole("button", { name: /넣기/ })).toBeNull();
  });

  it("2,200자를 넘으면 경고한다", () => {
    setup({
      preview: { caption: "x", length: 2201, fetching: false, error: false },
    });
    expect(screen.getByRole("alert")).toHaveTextContent("2,200자를 넘었어요");
    expect(screen.getByText("2,201자 / 2,200자")).toBeInTheDocument();
  });

  it("미리보기 오류를 알린다", async () => {
    setup({
      preview: { caption: null, length: 0, fetching: false, error: true },
    });
    await userEvent.click(screen.getByRole("tab", { name: "미리보기" }));
    expect(screen.getByRole("alert")).toHaveTextContent(
      "미리보기를 불러오지 못했어요",
    );
  });
});
