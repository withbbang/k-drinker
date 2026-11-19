import { describe, expect, it } from "vitest";

import {
  MOUSE_ACTIVATION,
  moveByKey,
  TOUCH_ACTIVATION,
} from "@/components/ui/photo-strip";

const key = (s: string) => s;

describe("moveByKey", () => {
  it("앞 항목을 뒤로 옮긴다", () => {
    expect(moveByKey(["a", "b", "c"], key, "a", "c")).toEqual(["b", "c", "a"]);
  });

  it("뒤 항목을 앞으로 옮긴다", () => {
    expect(moveByKey(["a", "b", "c"], key, "c", "a")).toEqual(["c", "a", "b"]);
  });

  it("없는 키나 같은 키면 같은 배열을 돌려준다", () => {
    const items = ["a", "b", "c"];
    expect(moveByKey(items, key, "x", "a")).toBe(items);
    expect(moveByKey(items, key, "a", "x")).toBe(items);
    expect(moveByKey(items, key, "a", "a")).toBe(items);
  });
});

describe("끌기 시작 조건", () => {
  it("손가락은 250ms 눌러야 시작한다", () => {
    expect(TOUCH_ACTIVATION).toEqual({ delay: 250, tolerance: 5 });
  });

  it("마우스는 5px 움직여야 시작한다", () => {
    expect(MOUSE_ACTIVATION).toEqual({ distance: 5 });
  });
});
