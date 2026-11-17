import { Suspense } from "react";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { AppNav } from "@/components/layout/AppNav";

export default function NewBreweryPage() {
  return (
    <>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
      <main className="mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6">
        <h1 className="text-2xl font-semibold tracking-tight">새 양조장</h1>
        <BreweryForm />
      </main>
    </>
  );
}
