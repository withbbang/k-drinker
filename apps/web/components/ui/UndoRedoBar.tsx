const iconButton =
  "flex size-11 items-center justify-center rounded-full text-text focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-40";

function Icon({ d }: { d: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-5"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={d} />
    </svg>
  );
}

export function UndoRedoBar({
  canUndo,
  canRedo,
  disabled,
  onUndo,
  onRedo,
}: {
  canUndo: boolean;
  canRedo: boolean;
  disabled?: boolean;
  onUndo: () => void;
  onRedo: () => void;
}) {
  return (
    <div className="flex">
      <button
        type="button"
        aria-label="되돌리기"
        disabled={disabled || !canUndo}
        onClick={onUndo}
        className={iconButton}
      >
        <Icon d="M9 14L4 9l5-5M4 9h10a6 6 0 010 12h-3" />
      </button>
      <button
        type="button"
        aria-label="다시 하기"
        disabled={disabled || !canRedo}
        onClick={onRedo}
        className={iconButton}
      >
        <Icon d="M15 14l5-5-5-5M20 9H10a6 6 0 000 12h3" />
      </button>
    </div>
  );
}
