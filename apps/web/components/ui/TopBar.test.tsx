import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { TopBar } from "@/components/ui/TopBar";

describe("TopBar", () => {
  it("뒤로 링크·제목·actions를 그린다", () => {
    render(
      <TopBar
        title="수정"
        backHref="/posts/1"
        actions={<button>저장</button>}
      />,
    );
    expect(screen.getByRole("link", { name: "뒤로" })).toHaveAttribute(
      "href",
      "/posts/1",
    );
    expect(screen.getByRole("heading", { name: "수정" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });
});
