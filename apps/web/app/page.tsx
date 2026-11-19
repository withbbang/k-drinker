import { Suspense } from "react";

import { AppNav, TAB_SAFE_PADDING } from "@/components/layout/AppNav";
import { PostList } from "@/components/posts/PostList";

export default function Home() {
  return (
    <>
      <main
        className={`mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6 ${TAB_SAFE_PADDING}`}
      >
        <h1 className="text-2xl font-semibold tracking-tight">기록</h1>
        <Suspense fallback={null}>
          <PostList />
        </Suspense>
      </main>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
    </>
  );
}
