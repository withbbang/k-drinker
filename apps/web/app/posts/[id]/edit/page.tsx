import { Suspense } from "react";

import { AppNav } from "@/components/layout/AppNav";
import { PostEditForm } from "@/components/post-edit/PostEditForm";

async function EditContent({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <PostEditForm id={id} />;
}

export default function PostEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  return (
    <>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
      {/* 제목·저장은 PostEditForm의 상단 바가 맡는다. */}
      <main className="mx-auto flex w-full max-w-sm flex-col">
        <Suspense fallback={null}>
          <EditContent params={params} />
        </Suspense>
      </main>
    </>
  );
}
