import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

const IN_PROGRESS = ["RECEIVED", "ANALYZING", "PUBLISHING"];

export function usePost(id: string, options: { poll?: boolean } = {}) {
  return useQuery({
    queryKey: ["posts", id],
    queryFn: () => apiFetch<PostDetailResponse>(`/api/v1/posts/${id}`),
    // 진행 중인 상태일 때만 3초 간격으로 다시 불러온다.
    refetchInterval: options.poll
      ? (query) =>
          IN_PROGRESS.includes(query.state.data?.status ?? "") ? 3000 : false
      : false,
  });
}
