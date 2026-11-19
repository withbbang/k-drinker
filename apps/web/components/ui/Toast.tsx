import type { ToastState } from "@/hooks/use-toast";

export function Toast({
  toast,
  onDismiss,
}: {
  toast: ToastState | null;
  onDismiss: () => void;
}) {
  const action = toast?.action;
  // 알림 영역(role="status")은 늘 붙여 두고 안의 내용만 바꾼다. 새로 붙는 영역은 화면 읽기 프로그램이 놓칠 수 있다.
  return (
    <div
      role="status"
      className="pointer-events-none fixed inset-x-0 bottom-4 z-50 flex justify-center px-4"
    >
      {toast && (
        <div
          key={toast.id}
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
      )}
    </div>
  );
}
