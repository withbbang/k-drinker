import { Suspense, type ReactNode } from "react";

import { AppNav, TAB_SAFE_PADDING } from "@/components/layout/AppNav";

// 화면 아래 탭을 쓰는 맨 위 화면(기록·양조장·설정)의 공통 틀.
// 탭이 fixed라 본문 아래에 탭 높이만큼 여백을 둔다.
export default function TabsLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <main
        className={`mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6 ${TAB_SAFE_PADDING}`}
      >
        {children}
      </main>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
    </>
  );
}
