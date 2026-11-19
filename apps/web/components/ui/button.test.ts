import { describe, expect, it } from "vitest";

import { buttonClass } from "@/components/ui/button";

describe("buttonClass", () => {
  it("primary는 강조색 알약형이다", () => {
    const cls = buttonClass("primary");
    expect(cls).toContain("bg-accent");
    expect(cls).toContain("rounded-full");
  });
  it("primary·secondary는 넓은 가로 여백을 가진다", () => {
    expect(buttonClass("primary")).toContain("px-5");
    expect(buttonClass("secondary")).toContain("px-5");
  });
  it("글자 버튼도 누르는 영역이 44px 이상이다", () => {
    for (const variant of ["text", "danger"] as const) {
      const cls = buttonClass(variant);
      expect(cls).toContain("min-h-11");
      expect(cls).toContain("min-w-11");
      expect(cls).toContain("px-3");
    }
  });
  it("danger는 text와 같은 모양에 위험색이다", () => {
    const cls = buttonClass("danger");
    expect(cls).toContain("text-danger");
    expect(cls).not.toContain("text-accent");
    expect(cls).not.toContain("bg-accent");
  });
  it("extra 클래스를 덧붙인다", () => {
    expect(buttonClass("text", "ml-2")).toContain("ml-2");
  });
});
