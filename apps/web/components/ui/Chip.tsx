export function Chip({
  label,
  onActivate,
  onClear,
  disabled,
}: {
  label: string;
  onActivate: () => void;
  // 없으면 × 버튼을 그리지 않는다.
  onClear?: () => void;
  disabled?: boolean;
}) {
  return (
    <span className="inline-flex min-h-11 items-center rounded-field border border-line bg-surface text-text">
      <button
        type="button"
        onClick={onActivate}
        disabled={disabled}
        className={`min-h-11 text-sm ${onClear ? "rounded-l-field pl-3 pr-1" : "rounded-field px-3"} focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40`}
      >
        {label}
      </button>
      {onClear && (
        <button
          type="button"
          aria-label={`${label} 지우기`}
          onClick={onClear}
          disabled={disabled}
          className="flex size-11 items-center justify-center rounded-r-field text-muted focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
        >
          <span aria-hidden="true">×</span>
        </button>
      )}
    </span>
  );
}
