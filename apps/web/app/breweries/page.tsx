import { Suspense } from "react";

import Link from "next/link";

import { BreweryList } from "@/components/breweries/BreweryList";
import { AppNav, TAB_SAFE_PADDING } from "@/components/layout/AppNav";
import { buttonClass } from "@/components/ui/button";

export default function BreweriesPage() {
  return (
    <>
      <main
        className={`mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-6 ${TAB_SAFE_PADDING}`}
      >
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">양조장</h1>
          <Link href="/breweries/new" className={buttonClass("text")}>
            추가
          </Link>
        </div>
        <Suspense fallback={null}>
          <BreweryList />
        </Suspense>
      </main>
      <Suspense fallback={null}>
        <AppNav />
      </Suspense>
    </>
  );
}
