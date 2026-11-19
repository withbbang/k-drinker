import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Chip } from "@/components/ui/Chip";

describe("Chip", () => {
  it("본문은 onActivate, ×는 onClear를 부른다", async () => {
    const onActivate = vi.fn();
    const onClear = vi.fn();
    render(<Chip label="X" onActivate={onActivate} onClear={onClear} />);
    await userEvent.click(screen.getByRole("button", { name: "X" }));
    expect(onActivate).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "X 지우기" }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });
});
