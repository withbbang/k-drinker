"use client";

import { useState } from "react";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useLogout } from "@/hooks/use-logout";

const ITEMS = [
  { href: "/", label: "기록" },
  { href: "/breweries", label: "양조장" },
  { href: "/settings", label: "설정" },
] as const;

export function AppNav() {
  const pathname = usePathname();
  const logout = useLogout();
  const [confirming, setConfirming] = useState(false);

  return (
    <nav
      aria-label="주 메뉴"
      className="flex gap-2 border-b border-neutral-200 bg-white px-4 py-2"
    >
      {ITEMS.map(({ href, label }) => {
        const current = pathname === href;
        return (
          <Link
            key={href}
            href={href}
            aria-current={current ? "page" : undefined}
            className={`rounded-full px-4 py-2 text-sm ${current ? "bg-neutral-900 text-white" : "text-neutral-700"}`}
          >
            {label}
          </Link>
        );
      })}
      <button
        type="button"
        onClick={() => setConfirming(true)}
        disabled={logout.isPending}
        className="ml-auto rounded-full px-4 py-2 text-sm text-neutral-700 disabled:opacity-50"
      >
        로그아웃
      </button>
      {confirming && (
        <ConfirmDialog
          title="로그아웃할까요?"
          confirmLabel="로그아웃"
          pending={logout.isPending}
          onConfirm={() => {
            setConfirming(false);
            logout.mutate();
          }}
          onClose={() => setConfirming(false)}
        />
      )}
    </nav>
  );
}
