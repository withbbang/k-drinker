"use client";

import { useSearchParams } from "next/navigation";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { Skeleton } from "@/components/ui/Skeleton";
import { TopBar } from "@/components/ui/TopBar";
import { useBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";

export function BreweryDetail({ id }: { id: string }) {
  const brewery = useBrewery(id);
  const justCreated = useSearchParams().get("saved") === "1";

  if (brewery.isPending) {
    return (
      <>
        <TopBar title="양조장" backHref="/breweries" />
        <div
          role="status"
          aria-label="불러오는 중"
          className="flex flex-col gap-4 px-4 pt-4"
        >
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      </>
    );
  }
  if (brewery.isError) {
    return (
      <>
        <TopBar title="양조장" backHref="/breweries" />
        <p role="alert" className="px-4 pt-4 text-sm text-danger">
          {brewery.error instanceof ApiError
            ? brewery.error.message
            : "양조장을 불러오지 못했어요."}
        </p>
      </>
    );
  }
  return (
    <>
      <TopBar title={brewery.data.name} backHref="/breweries" />
      <div className="px-4 pt-4">
        <BreweryForm brewery={brewery.data} justCreated={justCreated} />
      </div>
    </>
  );
}
