import { describe, expect, it } from "vitest";

import { safeNext } from "@/lib/safe-next";

describe("safeNext", () => {
  it("keeps a same-site path with query", () => {
    expect(safeNext("/share?id=1")).toBe("/share?id=1");
  });

  it("keeps a percent-encoded tab, which stays a path", () => {
    expect(safeNext("/%09/evil.com")).toBe("/%09/evil.com");
  });

  it.each([
    ["//evil.com"],
    ["/\\evil.com"],
    ["https://x"],
    ["javascript:alert(1)"],
    ["/\t/evil.com"],
    ["/\n/evil.com"],
    ["/\r/evil.com"],
    ["share"],
    [""],
    [null],
    [undefined],
  ])("falls back to home for %s", (value) => {
    expect(safeNext(value)).toBe("/");
  });
});
