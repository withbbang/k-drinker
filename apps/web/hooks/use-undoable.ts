import { useCallback, useReducer } from "react";

type UndoableState<T> = { past: T[]; present: T; future: T[] };

type Action<T> =
  | { type: "set"; next: T | ((prev: T) => T); limit: number }
  | { type: "undo" }
  | { type: "redo" };

// 스냅숏 스택 리듀서. 같은 값이면 기록하지 않고, limit를 넘으면 가장 오래된 것을 버린다.
export function undoableReducer<T>(
  state: UndoableState<T>,
  action: Action<T>,
): UndoableState<T> {
  switch (action.type) {
    case "set": {
      const next =
        typeof action.next === "function"
          ? (action.next as (prev: T) => T)(state.present)
          : action.next;
      if (Object.is(next, state.present)) return state;
      return {
        past: [...state.past, state.present].slice(-action.limit),
        present: next,
        future: [],
      };
    }
    case "undo": {
      if (state.past.length === 0) return state;
      return {
        past: state.past.slice(0, -1),
        present: state.past[state.past.length - 1],
        future: [state.present, ...state.future],
      };
    }
    case "redo": {
      if (state.future.length === 0) return state;
      return {
        past: [...state.past, state.present],
        present: state.future[0],
        future: state.future.slice(1),
      };
    }
  }
}

export function useUndoable<T>(initial: T, limit = 50) {
  const [state, dispatch] = useReducer(undoableReducer<T>, {
    past: [],
    present: initial,
    future: [],
  });
  const set = useCallback(
    (next: T | ((prev: T) => T)) => dispatch({ type: "set", next, limit }),
    [limit],
  );
  const undo = useCallback(() => dispatch({ type: "undo" }), []);
  const redo = useCallback(() => dispatch({ type: "redo" }), []);
  return {
    state: state.present,
    set,
    undo,
    redo,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0,
  };
}
