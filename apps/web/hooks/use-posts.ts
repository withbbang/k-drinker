import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PageResponse } from "@/types/api/common";
import type { PostSummaryResponse } from "@/types/api/post";

export function usePosts(page: number) {
  return useQuery({
    queryKey: ["posts", "list", page],
    queryFn: () =>
      apiFetch<PageResponse<PostSummaryResponse>>(
        `/api/v1/posts?page=${page}&size=20`,
      ),
  });
}
