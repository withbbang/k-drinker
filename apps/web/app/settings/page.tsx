import { Suspense } from "react";

import { AppNav, TAB_SAFE_PADDING } from "@/components/layout/AppNav";
import { LogoutButton } from "@/components/settings/LogoutButton";
import { InstagramTokenPanel } from "@/components/settings/InstagramTokenPanel";

export default function SettingsPage() {
  return (
    <>
      <main
        className={`mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6 ${TAB_SAFE_PADDING}`}
      >
        <h1 className="text-2xl font-semibold tracking-tight">설정</h1>
        <InstagramTokenPanel />
        <LogoutButton />
      </main>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
    </>
  );
}
