"use client";

import { useSearchParams } from "next/navigation";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { SkeletonList } from "@/components/ui/Skeleton";
import { TopBar } from "@/components/ui/TopBar";
import { useBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";

export function BreweryDetail({ id }: { id: string }) {
  const brewery = useBrewery(id);
  const justCreated = useSearchParams().get("saved") === "1";

  let body;
  if (brewery.isPending) {
    body = (
      <SkeletonList
        count={6}
        itemClassName="h-16 w-full"
        className="flex flex-col gap-4"
      />
    );
  } else if (brewery.isError) {
    body = (
      <p role="alert" className="text-sm text-danger">
        {brewery.error instanceof ApiError
          ? brewery.error.message
          : "양조장을 불러오지 못했어요."}
      </p>
    );
  } else {
    body = <BreweryForm brewery={brewery.data} justCreated={justCreated} />;
  }

  return (
    <>
      <TopBar title={brewery.data?.name ?? "양조장"} backHref="/breweries" />
      <div className="px-4 pt-4">{body}</div>
    </>
  );
}
