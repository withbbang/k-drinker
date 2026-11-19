import { useCallback, useReducer } from "react";

type TypingGroup = { key: string; at: number };

export type UndoableState<T> = {
  past: T[];
  present: T;
  future: T[];
  // 이어 치는 중인 글자칸. 같은 칸을 windowMs 안에 또 바꾸면 한 번으로 묶는다.
  group: TypingGroup | null;
};

type Action<T> =
  | {
      type: "set";
      next: T | ((prev: T) => T);
      limit: number;
      group?: TypingGroup & { windowMs: number };
    }
  | { type: "undo" }
  | { type: "redo" }
  | { type: "endGroup" };

// 같은 값인지. 일반 객체는 필드 하나씩 Object.is로 본다(updater가 늘 새 객체를 만들기 때문).
function sameValue<T>(a: T, b: T): boolean {
  if (Object.is(a, b)) return true;
  if (
    typeof a !== "object" ||
    typeof b !== "object" ||
    a === null ||
    b === null ||
    Array.isArray(a) ||
    Array.isArray(b)
  ) {
    return false;
  }
  const keysA = Object.keys(a) as (keyof T)[];
  return (
    keysA.length === Object.keys(b).length &&
    keysA.every((key) => Object.hasOwn(b, key) && Object.is(a[key], b[key]))
  );
}

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
      if (sameValue(next, state.present)) return state;
      const { group } = action;
      if (
        group &&
        state.group?.key === group.key &&
        group.at - state.group.at <= group.windowMs
      ) {
        return {
          ...state,
          present: next,
          future: [],
          group: { key: group.key, at: group.at },
        };
      }
      return {
        past: [...state.past, state.present].slice(-action.limit),
        present: next,
        future: [],
        group: group ? { key: group.key, at: group.at } : null,
      };
    }
    case "undo": {
      if (state.past.length === 0) return state;
      return {
        past: state.past.slice(0, -1),
        present: state.past[state.past.length - 1],
        future: [state.present, ...state.future],
        group: null,
      };
    }
    case "redo": {
      if (state.future.length === 0) return state;
      return {
        past: [...state.past, state.present],
        present: state.future[0],
        future: state.future.slice(1),
        group: null,
      };
    }
    case "endGroup":
      return state.group ? { ...state, group: null } : state;
  }
}

export function useUndoable<T>(
  initial: T,
  { limit = 50, groupMs = 1000 }: { limit?: number; groupMs?: number } = {},
) {
  const [state, dispatch] = useReducer(undoableReducer<T>, {
    past: [],
    present: initial,
    future: [],
    group: null,
  });
  const set = useCallback(
    (next: T | ((prev: T) => T)) => dispatch({ type: "set", next, limit }),
    [limit],
  );
  // 같은 key로 groupMs 안에 이어 바꾸면 되돌리기 한 단계로 묶는다.
  const setTyping = useCallback(
    (key: string, next: T | ((prev: T) => T)) =>
      dispatch({
        type: "set",
        next,
        limit,
        group: { key, at: Date.now(), windowMs: groupMs },
      }),
    [limit, groupMs],
  );
  const endTyping = useCallback(() => dispatch({ type: "endGroup" }), []);
  const undo = useCallback(() => dispatch({ type: "undo" }), []);
  const redo = useCallback(() => dispatch({ type: "redo" }), []);
  return {
    state: state.present,
    set,
    setTyping,
    endTyping,
    undo,
    redo,
    canUndo: state.past.length > 0,
    canRedo: state.future.length > 0,
  };
}
