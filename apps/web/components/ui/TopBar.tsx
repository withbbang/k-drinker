import Link from "next/link";
import type { ReactNode } from "react";

export function TopBar({
  title,
  backHref,
  actions,
}: {
  title: string;
  backHref: string;
  actions?: ReactNode;
}) {
  return (
    <header className="sticky top-0 z-30 flex min-h-14 items-center gap-2 border-b border-line bg-bg px-2">
      <Link
        href={backHref}
        aria-label="뒤로"
        className="flex size-11 items-center justify-center rounded-full text-text focus-visible:outline-2 focus-visible:outline-accent"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          className="size-6"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
        >
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </Link>
      <h1 className="flex-1 truncate text-center text-base font-semibold text-text">
        {title}
      </h1>
      <div className="flex min-w-11 items-center justify-end gap-1">
        {actions}
      </div>
    </header>
  );
}
