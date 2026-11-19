"use client";

import { useState } from "react";

import { buttonClass } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { useLogout } from "@/hooks/use-logout";

export function LogoutButton() {
  const logout = useLogout();
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <button
        type="button"
        onClick={() => setConfirming(true)}
        disabled={logout.isPending}
        className={buttonClass("secondary", "w-full")}
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
    </>
  );
}
