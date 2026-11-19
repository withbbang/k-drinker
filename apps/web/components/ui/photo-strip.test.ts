import { afterEach, describe, expect, it, vi } from "vitest";

import {
  makeAnnouncements,
  MOUSE_ACTIVATION,
  moveByKey,
  prefersReducedMotion,
  SCREEN_READER_INSTRUCTIONS,
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

describe("한국어 안내", () => {
  it("끌기 안내 문구가 한국어다", () => {
    expect(SCREEN_READER_INSTRUCTIONS.draggable).toContain("화살표");
    const a = makeAnnouncements(["a", "b", "c"]);
    expect(a.onDragStart({ active: { id: "b" } })).toBe(
      "2번째 사진을 들었어요.",
    );
    expect(a.onDragEnd({ active: { id: "a" }, over: { id: "c" } })).toBe(
      "1번째 사진을 3번째 자리로 옮겼어요.",
    );
  });
});

describe("prefersReducedMotion", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("matchMedia가 없으면 거짓", () => {
    vi.stubGlobal("matchMedia", undefined);
    expect(prefersReducedMotion()).toBe(false);
  });

  it("모션 감소면 참", () => {
    vi.stubGlobal("matchMedia", () => ({ matches: true }));
    expect(prefersReducedMotion()).toBe(true);
  });
});
