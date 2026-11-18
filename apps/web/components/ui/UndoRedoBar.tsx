const buttonClass =
  "min-h-11 min-w-11 rounded-md border border-neutral-300 px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40";

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
    <div className="flex gap-2">
      <button
        type="button"
        disabled={disabled || !canUndo}
        onClick={onUndo}
        className={buttonClass}
      >
        되돌리기
      </button>
      <button
        type="button"
        disabled={disabled || !canRedo}
        onClick={onRedo}
        className={buttonClass}
      >
        다시 하기
      </button>
    </div>
  );
}
