"use client";

import type { ReactNode } from "react";

import Link from "next/link";
import { usePathname } from "next/navigation";

// 탭을 쓰는 화면의 main에 붙여 마지막 내용이 탭에 가려지지 않게 한다.
export const TAB_SAFE_PADDING = "pb-[calc(4rem+env(safe-area-inset-bottom))]";

const ICON_PROPS = {
  width: 24,
  height: 24,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const;

const ITEMS: { href: string; label: string; icon: ReactNode }[] = [
  {
    href: "/",
    label: "기록",
    // 술잔
    icon: (
      <svg {...ICON_PROPS}>
        <path d="M6 3h12l-1.5 11a4.5 4.5 0 0 1-9 0L6 3Z" />
        <path d="M12 18.5V21M8.5 21h7" />
      </svg>
    ),
  },
  {
    href: "/breweries",
    label: "양조장",
    // 집
    icon: (
      <svg {...ICON_PROPS}>
        <path d="M3 11.5 12 4l9 7.5" />
        <path d="M5.5 10v10h13V10" />
        <path d="M10 20v-5h4v5" />
      </svg>
    ),
  },
  {
    href: "/settings",
    label: "설정",
    // 톱니
    icon: (
      <svg {...ICON_PROPS}>
        <circle cx="12" cy="12" r="3" />
        <path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M18.7 5.3l-2.1 2.1M7.4 16.6l-2.1 2.1" />
      </svg>
    ),
  },
];

export function AppNav() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="주 메뉴"
      className="border-line bg-bg fixed inset-x-0 bottom-0 z-30 border-t pb-[env(safe-area-inset-bottom)]"
    >
      <div className="mx-auto flex max-w-sm">
        {ITEMS.map(({ href, label, icon }) => {
          const current =
            href === "/" ? pathname === "/" : pathname.startsWith(href);
          return (
            <Link
              key={href}
              href={href}
              aria-current={current ? "page" : undefined}
              className={`flex min-h-16 flex-1 flex-col items-center justify-center gap-1 text-xs ${current ? "text-accent" : "text-muted"}`}
            >
              {icon}
              {label}
            </Link>
          );
        })}
      </div>
    </nav>
  );
}
