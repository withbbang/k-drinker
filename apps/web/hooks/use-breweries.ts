import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { BreweryRequest, BreweryResponse } from "@/types/api/brewery";
import type { PageResponse } from "@/types/api/common";

export function useBreweries(q: string, page: number) {
  return useQuery({
    queryKey: ["breweries", "list", q, page],
    queryFn: () =>
      apiFetch<PageResponse<BreweryResponse>>(
        `/api/v1/breweries?q=${encodeURIComponent(q)}&page=${page}&size=20`,
      ),
  });
}

/** id가 null이면 부르지 않는다. */
export function useBrewery(id: string | number | null) {
  return useQuery({
    queryKey: ["breweries", "detail", String(id)],
    queryFn: () => apiFetch<BreweryResponse>(`/api/v1/breweries/${id}`),
    enabled: id !== null,
  });
}

/** id가 없으면 새로 만들고(POST), 있으면 고친다(PUT). */
export function useSaveBrewery(id: number | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: BreweryRequest) =>
      apiFetch<BreweryResponse>(
        id === null ? "/api/v1/breweries" : `/api/v1/breweries/${id}`,
        {
          method: id === null ? "POST" : "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["breweries"] }),
  });
}
