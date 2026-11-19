import { readFileSync } from "node:fs";
import { join } from "node:path";

import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppNav } from "@/components/layout/AppNav";

const { pathnameMock } = vi.hoisted(() => ({ pathnameMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => pathnameMock(),
}));

describe("AppNav", () => {
  beforeEach(() => {
    pathnameMock.mockReturnValue("/breweries");
  });

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

  it("keeps the brewery tab current on a sub page", () => {
    pathnameMock.mockReturnValue("/breweries/12");
    render(<AppNav />);

    expect(screen.getByRole("link", { name: "양조장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "기록" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("marks the record tab only on the root", () => {
    pathnameMock.mockReturnValue("/");
    render(<AppNav />);

    expect(screen.getByRole("link", { name: "기록" })).toHaveAttribute(
      "aria-current",
      "page",
    );
  });

  it("has no logout button", () => {
    render(<AppNav />);

    expect(
      screen.queryByRole("button", { name: "로그아웃" }),
    ).not.toBeInTheDocument();
  });

  it.each(["app/page.tsx", "app/breweries/page.tsx", "app/settings/page.tsx"])(
    "%s reserves room for the tab bar",
    (file) => {
      const source = readFileSync(join(process.cwd(), file), "utf8");

      expect(source).toMatch(/import \{[^}]*TAB_SAFE_PADDING[^}]*\}/);
      expect(source).toMatch(/\$\{TAB_SAFE_PADDING\}/);
    },
  );
});
