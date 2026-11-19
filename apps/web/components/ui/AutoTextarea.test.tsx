import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { AutoTextarea } from "@/components/ui/AutoTextarea";

describe("AutoTextarea", () => {
  it("여러 줄 입력을 그대로 유지한다", async () => {
    render(<AutoTextarea aria-label="본문" />);
    const box = screen.getByRole("textbox", { name: "본문" });
    await userEvent.type(box, "가{Enter}나");
    expect(box).toHaveValue("가\n나");
  });
});
