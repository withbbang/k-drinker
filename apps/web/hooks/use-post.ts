import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

export function usePost(id: string) {
  return useQuery({
    queryKey: ["posts", id],
    queryFn: () => apiFetch<PostDetailResponse>(`/api/v1/posts/${id}`),
  });
}
