import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AppNav } from "@/components/layout/AppNav";

vi.mock("next/navigation", () => ({
  usePathname: () => "/breweries",
}));

describe("AppNav", () => {
  it("marks the current page", () => {
    render(<AppNav />);

    expect(screen.getByRole("link", { name: "양조장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "기록" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByRole("link", { name: "설정" })).toHaveAttribute(
      "href",
      "/settings",
    );
  });
});
