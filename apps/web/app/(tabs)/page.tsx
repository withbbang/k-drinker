import { Suspense } from "react";

import { PostList } from "@/components/posts/PostList";

export default function Home() {
  return (
    <>
      <h1 className="text-2xl font-semibold tracking-tight">기록</h1>
      <Suspense fallback={null}>
        <PostList />
      </Suspense>
    </>
  );
}
