"use client";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { TopBar } from "@/components/ui/TopBar";
import { useBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";

export function BreweryDetail({ id }: { id: string }) {
  const brewery = useBrewery(id);

  if (brewery.isPending) {
    return (
      <>
        <TopBar title="양조장" backHref="/breweries" />
        <p className="px-4 pt-4 text-sm text-muted">불러오는 중…</p>
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
        <BreweryForm brewery={brewery.data} />
      </div>
    </>
  );
}
