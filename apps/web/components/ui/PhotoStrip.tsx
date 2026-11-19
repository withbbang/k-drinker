"use client";

import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  horizontalListSortingStrategy,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { ReactNode } from "react";

import { AddPhotoTile } from "@/components/ui/AddPhotoTile";
import {
  MOUSE_ACTIVATION,
  makeAnnouncements,
  moveByKey,
  prefersReducedMotion,
  SCREEN_READER_INSTRUCTIONS,
  TOUCH_ACTIVATION,
} from "@/components/ui/photo-strip";

type PhotoStripProps<T> = {
  items: T[];
  getKey: (item: T) => string;
  renderImage: (item: T, className: string) => ReactNode;
  // 주면 여백 상태를 썸네일에 보인다
  isPadded?: (item: T) => boolean;
  disabled?: boolean;
  max: number;
  onReorder: (next: T[]) => void;
  // 확인창 없음. 토스트는 부모가 띄운다
  onRemove: (key: string) => void;
  // 주면 썸네일을 누를 때 부른다
  onOpen?: (key: string) => void;
  // 끌기 시작 true, 끝·취소 false
  onBusyChange?: (busy: boolean) => void;
  onAdd?: (files: File[]) => void;
  onOverflow?: (skipped: number) => void;
};

type ThumbProps<T> = {
  item: T;
  index: number;
  itemKey: string;
  removable: boolean;
  padded: boolean;
  disabled: boolean;
  renderImage: (item: T, className: string) => ReactNode;
  onRemove: (key: string) => void;
  onOpen?: (key: string) => void;
};

function Thumb<T>({
  item,
  index,
  itemKey,
  removable,
  padded,
  disabled,
  renderImage,
  onRemove,
  onOpen,
}: ThumbProps<T>) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: itemKey, disabled });
  const number = index + 1;
  const first = index === 0;

  return (
    <li
      ref={setNodeRef}
      // dnd-kit이 계산하는 transform·transition은 Tailwind 클래스로 줄 수 없어 style로만 준다.
      style={{
        transform: CSS.Transform.toString(transform),
        transition: prefersReducedMotion() ? undefined : transition,
      }}
      className={`relative flex w-20 shrink-0 flex-col gap-1 ${
        isDragging ? "z-10" : ""
      }`}
    >
      <span
        aria-hidden="true"
        className="h-4 text-xs font-semibold leading-4 text-accent"
      >
        {first ? "대표" : ""}
      </span>
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label={first ? `${number}번째 사진, 대표` : `${number}번째 사진`}
        onClick={() => onOpen?.(itemKey)}
        className={`block aspect-[4/5] w-full touch-manipulation overflow-hidden rounded-thumb border border-line bg-bg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent motion-safe:transition-[opacity,box-shadow] ${
          isDragging ? "opacity-70 ring-2 ring-accent" : ""
        }`}
      >
        {renderImage(
          item,
          `h-full w-full pointer-events-none select-none ${
            padded ? "object-contain bg-surface" : "object-cover"
          }`,
        )}
        {padded && <span className="sr-only">여백 있음</span>}
      </button>
      {removable && (
        <button
          type="button"
          aria-label={`${number}번째 사진 빼기`}
          disabled={disabled}
          onClick={() => onRemove(itemKey)}
          className="absolute right-[-6px] top-[10px] flex size-6 items-center justify-center rounded-full border border-line bg-bg text-sm leading-none text-text after:absolute after:-inset-3 after:content-[''] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-40"
        >
          <span aria-hidden="true">×</span>
        </button>
      )}
    </li>
  );
}

// 가로로 스크롤되는 사진 썸네일 줄. 길게 눌러 끌어 순서를 바꾼다.
export function PhotoStrip<T>({
  items,
  getKey,
  renderImage,
  isPadded,
  disabled = false,
  max,
  onReorder,
  onRemove,
  onOpen,
  onBusyChange,
  onAdd,
  onOverflow,
}: PhotoStripProps<T>) {
  const sensors = useSensors(
    useSensor(TouchSensor, { activationConstraint: TOUCH_ACTIVATION }),
    useSensor(MouseSensor, { activationConstraint: MOUSE_ACTIVATION }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );
  const keys = items.map(getKey);
  const remaining = max - items.length;

  const handleDragEnd = (event: DragEndEvent) => {
    onBusyChange?.(false);
    const { active, over } = event;
    if (!over) return;
    const next = moveByKey(items, getKey, String(active.id), String(over.id));
    if (next !== items) onReorder(next);
  };

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      accessibility={{
        announcements: makeAnnouncements(keys),
        screenReaderInstructions: SCREEN_READER_INSTRUCTIONS,
      }}
      onDragStart={() => onBusyChange?.(true)}
      onDragEnd={handleDragEnd}
      onDragCancel={() => onBusyChange?.(false)}
    >
      <SortableContext items={keys} strategy={horizontalListSortingStrategy}>
        <ul className="flex gap-3 overflow-x-auto px-2 pb-2 pt-1">
          {items.map((item, i) => (
            <Thumb
              key={keys[i]}
              item={item}
              index={i}
              itemKey={keys[i]}
              removable={items.length > 1}
              padded={isPadded?.(item) ?? false}
              disabled={disabled}
              renderImage={renderImage}
              onRemove={onRemove}
              onOpen={onOpen}
            />
          ))}
          {onAdd && remaining > 0 && (
            <li className="flex w-20 shrink-0 flex-col gap-1">
              <span aria-hidden="true" className="h-4" />
              <AddPhotoTile
                remaining={remaining}
                disabled={disabled}
                onAdd={onAdd}
                onOverflow={(skipped) => onOverflow?.(skipped)}
              />
            </li>
          )}
        </ul>
      </SortableContext>
    </DndContext>
  );
}
