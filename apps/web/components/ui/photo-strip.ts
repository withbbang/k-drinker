import { arrayMove } from "@dnd-kit/sortable";

// 손가락은 250ms 눌러야 끌기가 시작된다. 그 전에 5px 넘게 움직이면 가로 스크롤로 본다.
export const TOUCH_ACTIVATION = { delay: 250, tolerance: 5 } as const;
// 마우스는 5px 움직이면 끌기가 시작된다. 그보다 덜 움직이면 클릭이다.
export const MOUSE_ACTIVATION = { distance: 5 } as const;

// fromKey 항목을 toKey 항목 자리로 옮긴다. 같은 키거나 없는 키면 같은 배열을 돌려준다.
export function moveByKey<T>(
  items: T[],
  getKey: (item: T) => string,
  fromKey: string,
  toKey: string,
): T[] {
  if (fromKey === toKey) return items;
  const from = items.findIndex((item) => getKey(item) === fromKey);
  const to = items.findIndex((item) => getKey(item) === toKey);
  if (from < 0 || to < 0) return items;
  return arrayMove(items, from, to);
}

// 화면 낭독기 안내. dnd-kit 기본값은 영어라 한국어로 바꾼다.
export const SCREEN_READER_INSTRUCTIONS = {
  draggable:
    "스페이스나 엔터로 들고, 화살표로 옮긴 뒤 다시 눌러 놓으세요. 취소는 Esc예요.",
};

type DragIds = {
  active: { id: string | number };
  over: { id: string | number } | null;
};

// keys는 지금 줄 순서의 키 목록. 키를 몇 번째 사진인지로 바꿔 읽어 준다.
export function makeAnnouncements(keys: string[]) {
  const nth = (id: string | number) => keys.indexOf(String(id)) + 1;
  return {
    onDragStart: ({ active }: Pick<DragIds, "active">) =>
      `${nth(active.id)}번째 사진을 들었어요.`,
    onDragOver: ({ active, over }: DragIds) =>
      over
        ? `${nth(active.id)}번째 사진이 ${nth(over.id)}번째 자리 위에 있어요.`
        : undefined,
    onDragEnd: ({ active, over }: DragIds) =>
      over
        ? `${nth(active.id)}번째 사진을 ${nth(over.id)}번째 자리로 옮겼어요.`
        : `${nth(active.id)}번째 사진을 제자리에 놓았어요.`,
    onDragCancel: ({ active }: Pick<DragIds, "active">) =>
      `끌기를 취소했어요. ${nth(active.id)}번째 사진은 그대로예요.`,
  };
}

// matchMedia가 없는 환경(jsdom 등)에서는 모션 감소가 아닌 것으로 본다
export function prefersReducedMotion(): boolean {
  if (
    typeof window === "undefined" ||
    typeof window.matchMedia !== "function"
  ) {
    return false;
  }
  return (
    window.matchMedia("(prefers-reduced-motion: reduce)")?.matches ?? false
  );
}
