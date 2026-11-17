import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Pager } from "@/components/ui/Pager";

const hrefFor = (page: number) => `/?page=${page}`;

describe("Pager", () => {
  it("disables previous on the first page", () => {
    render(<Pager page={0} totalPages={3} hrefFor={hrefFor} />);

    expect(screen.getByText("이전")).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "다음" })).toHaveAttribute(
      "href",
      "/?page=1",
    );
  });

  it("disables next on the last page", () => {
    render(<Pager page={2} totalPages={3} hrefFor={hrefFor} />);

    expect(screen.getByText("다음")).toHaveAttribute("aria-disabled", "true");
    expect(screen.getByRole("link", { name: "이전" })).toHaveAttribute(
      "href",
      "/?page=1",
    );
  });
});
