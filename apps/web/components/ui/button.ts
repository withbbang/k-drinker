type ButtonVariant = "primary" | "secondary" | "text";

const base =
  "inline-flex min-h-11 items-center justify-center px-5 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:opacity-40";

const variants: Record<ButtonVariant, string> = {
  primary: "rounded-full bg-accent text-on-accent",
  secondary: "rounded-full border border-line bg-surface text-text",
  text: "text-accent",
};

export function buttonClass(variant: ButtonVariant, extra?: string): string {
  return [base, variants[variant], extra].filter(Boolean).join(" ");
}
