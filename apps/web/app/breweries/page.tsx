import { Suspense } from "react";

import { BreweryList } from "@/components/breweries/BreweryList";
import { AppNav } from "@/components/layout/AppNav";

export default function BreweriesPage() {
  return (
    <>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
      <main className="mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6">
        <h1 className="text-2xl font-semibold tracking-tight">양조장</h1>
        <Suspense fallback={null}>
          <BreweryList />
        </Suspense>
      </main>
    </>
  );
}
