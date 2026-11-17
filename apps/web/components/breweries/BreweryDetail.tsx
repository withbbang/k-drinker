"use client";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { useBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";

export function BreweryDetail({ id }: { id: string }) {
  const brewery = useBrewery(id);

  if (brewery.isPending) {
    return <p className="text-sm text-neutral-700">불러오는 중…</p>;
  }
  if (brewery.isError) {
    return (
      <p role="alert" className="text-sm text-red-700">
        {brewery.error instanceof ApiError
          ? brewery.error.message
          : "양조장을 불러오지 못했습니다."}
      </p>
    );
  }
  return <BreweryForm brewery={brewery.data} />;
}
