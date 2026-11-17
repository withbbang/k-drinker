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
      <main className="mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6">
        <h1 className="text-2xl font-semibold tracking-tight">후기 수정</h1>
        <Suspense fallback={null}>
          <EditContent params={params} />
        </Suspense>
      </main>
    </>
  );
}
