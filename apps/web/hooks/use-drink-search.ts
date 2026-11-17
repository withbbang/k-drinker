import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { DrinkResponse } from "@/types/api/drink";

export function useDrinkSearch(q: string) {
  const term = q.trim();
  return useQuery({
    queryKey: ["drinks", term],
    queryFn: () =>
      apiFetch<DrinkResponse[]>(`/api/v1/drinks?q=${encodeURIComponent(term)}`),
    enabled: term !== "",
  });
}
