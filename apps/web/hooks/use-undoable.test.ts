import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { undoableReducer, useUndoable } from "@/hooks/use-undoable";

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
    const start = { past: [], present: 0, future: [] };

    const set = undoableReducer(start, { type: "set", next: 1, limit: 50 });
    const undone = undoableReducer(set, { type: "undo" });
    const redone = undoableReducer(undone, { type: "redo" });

    expect(redone.present).toBe(1);
    expect(redone.past).toEqual([0]);
  });
});
