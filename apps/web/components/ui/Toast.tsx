import type { ToastState } from "@/hooks/use-toast";

export function Toast({
  toast,
  onDismiss,
}: {
  toast: ToastState | null;
  onDismiss: () => void;
}) {
  if (!toast) return null;
  const { action } = toast;
  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex justify-center px-4">
      <div
        key={toast.id}
        role="status"
        className="pointer-events-auto flex max-w-md items-center gap-3 rounded-field border border-line bg-surface py-1 pl-4 pr-2 text-sm text-text motion-safe:animate-[toast-in_150ms_ease-out]"
      >
        <span>{toast.message}</span>
        {action && (
          <button
            type="button"
            onClick={() => {
              action.onAction();
              onDismiss();
            }}
            className="min-h-11 px-2 font-semibold text-accent focus-visible:outline-2 focus-visible:outline-accent"
          >
            {action.label}
          </button>
        )}
      </div>
    </div>
  );
}
