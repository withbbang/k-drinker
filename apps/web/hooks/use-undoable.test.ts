import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  undoableReducer,
  useUndoable,
  type UndoableState,
} from "@/hooks/use-undoable";

describe("useUndoable", () => {
  it("set 두 번 뒤 undo 두 번이면 처음 값, canUndo=false", () => {
    const { result } = renderHook(() => useUndoable(0));

    act(() => result.current.set(1));
    act(() => result.current.set(2));
    act(() => result.current.undo());
    act(() => result.current.undo());

    expect(result.current.state).toBe(0);
    expect(result.current.canUndo).toBe(false);
    expect(result.current.canRedo).toBe(true);
  });

  it("undo 뒤 set 하면 canRedo=false", () => {
    const { result } = renderHook(() => useUndoable(0));

    act(() => result.current.set(1));
    act(() => result.current.undo());
    expect(result.current.canRedo).toBe(true);
    act(() => result.current.set(5));

    expect(result.current.canRedo).toBe(false);
  });

  it("limit=50에서 set 51번 뒤 undo는 50번까지만", () => {
    const { result } = renderHook(() => useUndoable(0));

    for (let i = 1; i <= 51; i++) act(() => result.current.set(i));
    let undone = 0;
    while (result.current.canUndo) {
      act(() => result.current.undo());
      undone++;
    }

    expect(undone).toBe(50);
    expect(result.current.state).toBe(1);
  });

  it("같은 값 set은 기록 안 함", () => {
    const { result } = renderHook(() => useUndoable(3));

    act(() => result.current.set(3));

    expect(result.current.canUndo).toBe(false);
  });

  it("필드 값이 모두 같은 새 객체 set은 기록 안 함", () => {
    // given
    const photos = ["a"];
    const { result } = renderHook(() => useUndoable({ photos, rating: 3 }));

    // when: 같은 별점을 다시 고른 것처럼 새 객체를 넘긴다
    act(() => result.current.set((prev) => ({ ...prev, rating: 3 })));

    // then
    expect(result.current.canUndo).toBe(false);
  });

  it("필드 하나라도 다르면 기록한다", () => {
    // given
    const { result } = renderHook(() =>
      useUndoable({ photos: ["a"], rating: 3 }),
    );

    // when: 내용이 같아도 다른 배열이면 다른 값으로 본다
    act(() => result.current.set((prev) => ({ ...prev, photos: ["a"] })));

    // then
    expect(result.current.canUndo).toBe(true);
  });

  it("함수 updater는 현재 값을 받는다", () => {
    const { result } = renderHook(() => useUndoable(1));

    act(() => {
      result.current.set((prev) => prev + 1);
      result.current.set((prev) => prev + 1);
    });

    expect(result.current.state).toBe(3);
  });
});

describe("undoableReducer", () => {
  it("redo는 undo한 값을 되살린다", () => {
    const start = { past: [], present: 0, future: [], group: null };

    const set = undoableReducer(start, { type: "set", next: 1, limit: 50 });
    const undone = undoableReducer(set, { type: "undo" });
    const redone = undoableReducer(undone, { type: "redo" });

    expect(redone.present).toBe(1);
    expect(redone.past).toEqual([0]);
  });

  describe("글자 입력 묶기", () => {
    type Form = { memo: string; caption: string; rating: number };
    const start: UndoableState<Form> = {
      past: [],
      present: { memo: "", caption: "", rating: 3 },
      future: [],
      group: null,
    };
    const type = (
      state: UndoableState<Form>,
      key: string,
      patch: Partial<Form>,
      at: number,
    ) =>
      undoableReducer(state, {
        type: "set",
        next: (prev) => ({ ...prev, ...patch }),
        limit: 50,
        group: { key, at, windowMs: 1000 },
      });

    it("메모를_1초_안에_이어_치면_한_번으로_기록된다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = type(s, "memo", { memo: "ab" }, 500);
      s = type(s, "memo", { memo: "abc" }, 900);

      expect(s.past).toHaveLength(1);
      expect(s.present.memo).toBe("abc");
      expect(undoableReducer(s, { type: "undo" }).present.memo).toBe("");
    });

    it("1초_넘게_멈췄다_치면_따로_기록된다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = type(s, "memo", { memo: "ab" }, 1500);

      expect(s.past).toHaveLength(2);
    });

    it("다른_칸으로_옮기면_따로_기록된다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = type(s, "caption", { caption: "x" }, 100);

      expect(s.past).toHaveLength(2);
    });

    it("endGroup_뒤에는_따로_기록된다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = undoableReducer(s, { type: "endGroup" });
      s = type(s, "memo", { memo: "ab" }, 100);

      expect(s.past).toHaveLength(2);
    });

    it("메모를_치고_별점을_바꾸면_되돌리기가_하나씩_돌아간다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = type(s, "memo", { memo: "ab" }, 300);
      s = undoableReducer(s, {
        type: "set",
        next: (prev) => ({ ...prev, rating: 5 }),
        limit: 50,
      });

      s = undoableReducer(s, { type: "undo" });
      expect(s.present).toEqual({ memo: "ab", caption: "", rating: 3 });
      s = undoableReducer(s, { type: "undo" });
      expect(s.present.memo).toBe("");
    });

    it("묶인_입력_뒤_undo하면_그룹이_풀린다", () => {
      let s = type(start, "memo", { memo: "a" }, 0);
      s = undoableReducer(s, { type: "undo" });

      expect(s.group).toBeNull();
    });
  });
});
