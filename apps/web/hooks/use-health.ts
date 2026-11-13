import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { HealthResponse } from "@/types/api/health";

export function useHealth() {
  return useQuery({
    queryKey: ["health"],
    queryFn: () => apiFetch<HealthResponse>("/api/v1/health"),
  });
}
