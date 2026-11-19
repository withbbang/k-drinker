type ButtonVariant = "primary" | "secondary" | "text" | "danger";

const base =
  "inline-flex min-h-11 items-center justify-center text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-40";

const variants: Record<ButtonVariant, string> = {
  primary: "rounded-full bg-accent px-5 text-on-accent",
  secondary: "rounded-full border border-line bg-surface px-5 text-text",
  // 글자 버튼도 누르는 영역을 44px 이상으로 둔다.
  text: "min-w-11 px-3 text-accent",
  danger: "min-w-11 px-3 text-danger",
};

export function buttonClass(variant: ButtonVariant, extra?: string): string {
  return [base, variants[variant], extra].filter(Boolean).join(" ");
}
