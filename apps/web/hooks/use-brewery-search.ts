import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PageResponse } from "@/types/api/common";

export function useBrewerySearch(q: string) {
  const term = q.trim();
  return useQuery({
    queryKey: ["breweries", term],
    queryFn: () =>
      apiFetch<PageResponse<BreweryResponse>>(
        `/api/v1/breweries?q=${encodeURIComponent(term)}&page=0&size=20`,
      ),
    enabled: term !== "",
  });
}
