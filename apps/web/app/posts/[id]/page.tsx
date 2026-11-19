import { Suspense } from "react";

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
      {/* 상단 바(술 이름·수정)는 PostOverview가 데이터를 받아 그린다. */}
      <main className="mx-auto flex min-h-dvh w-full max-w-sm flex-col">
        <Suspense fallback={null}>
          <OverviewContent params={params} />
        </Suspense>
      </main>
    </>
  );
}
