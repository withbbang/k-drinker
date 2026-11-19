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
