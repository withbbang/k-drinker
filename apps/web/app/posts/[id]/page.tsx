import { Suspense } from "react";

import { AppNav } from "@/components/layout/AppNav";
import { PostOverview } from "@/components/post-overview/PostOverview";

async function OverviewContent({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return <PostOverview id={id} />;
}

export default function PostOverviewPage({
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
        <h1 className="text-2xl font-semibold tracking-tight">후기</h1>
        <Suspense fallback={null}>
          <OverviewContent params={params} />
        </Suspense>
      </main>
    </>
  );
}
