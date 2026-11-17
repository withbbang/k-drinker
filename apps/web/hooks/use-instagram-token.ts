import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { InstagramTokenResponse } from "@/types/api/instagram";

const KEY = ["instagram", "token"];

export function useInstagramToken() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => apiFetch<InstagramTokenResponse>("/api/v1/instagram/token"),
  });
}

export function useReloadInstagramToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      apiFetch<InstagramTokenResponse>("/api/v1/instagram/token/reload", {
        method: "POST",
      }),
    onSuccess: (token) => queryClient.setQueryData(KEY, token),
  });
}
