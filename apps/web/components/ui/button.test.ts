import { describe, expect, it } from "vitest";

import { buttonClass } from "@/components/ui/button";

describe("buttonClass", () => {
  it("primary는 강조색 알약형이다", () => {
    const cls = buttonClass("primary");
    expect(cls).toContain("bg-accent");
    expect(cls).toContain("rounded-full");
  });
  it("extra 클래스를 덧붙인다", () => {
    expect(buttonClass("text", "ml-2")).toContain("ml-2");
  });
});
