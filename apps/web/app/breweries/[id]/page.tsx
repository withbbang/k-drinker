import { Suspense } from "react";

import { BreweryDetail } from "@/components/breweries/BreweryDetail";

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
    <main className="mx-auto flex w-full max-w-sm flex-col">
      <Suspense fallback={null}>
        <DetailContent params={params} />
      </Suspense>
    </main>
  );
}
