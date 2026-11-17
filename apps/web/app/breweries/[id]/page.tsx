import { Suspense } from "react";

import { BreweryDetail } from "@/components/breweries/BreweryDetail";
import { AppNav } from "@/components/layout/AppNav";

async function DetailContent({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <BreweryDetail id={id} />;
}

export default function BreweryPage({
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
        <h1 className="text-2xl font-semibold tracking-tight">양조장 정보</h1>
        <Suspense fallback={null}>
          <DetailContent params={params} />
        </Suspense>
      </main>
    </>
  );
}
