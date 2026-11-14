import { describe, expect, it } from "vitest";

import { safeNext } from "@/lib/safe-next";

describe("safeNext", () => {
  it("keeps a same-site path with query", () => {
    expect(safeNext("/share?id=1")).toBe("/share?id=1");
  });

  it.each([
    ["//evil.com"],
    ["/\\evil.com"],
    ["https://x"],
    ["javascript:alert(1)"],
    ["share"],
    [""],
    [null],
    [undefined],
  ])("falls back to home for %s", (value) => {
    expect(safeNext(value)).toBe("/");
  });
});
