import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

describe("fonts", () => {
  it("고운바탕은_미리_받지_않아_응답_헤더가_커지지_않는다", () => {
    // given
    const source = readFileSync(join(process.cwd(), "app/fonts.ts"), "utf8");
    const gowun = source.slice(source.indexOf("Gowun_Batang({"));

    // then
    expect(gowun).toMatch(/preload:\s*false/);
  });
});
