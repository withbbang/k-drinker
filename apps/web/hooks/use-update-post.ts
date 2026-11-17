import { useMutation, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PostDetailResponse, PostUpdateRequest } from "@/types/api/post";

export function useUpdatePost(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: PostUpdateRequest) =>
      apiFetch<PostDetailResponse>(`/api/v1/posts/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(["posts", id], saved);
      return queryClient.invalidateQueries({ queryKey: ["posts"] });
    },
  });
}
