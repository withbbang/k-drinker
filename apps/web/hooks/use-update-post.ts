import { useMutation, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PostDetailResponse, PostUpdateRequest } from "@/types/api/post";

export type UpdatePostInput = {
  request: PostUpdateRequest;
  newPhotos: File[];
};

export function useUpdatePost(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    // multipart로 보낸다. Content-Type(경계 포함)은 브라우저가 정하게 둔다.
    mutationFn: ({ request, newPhotos }: UpdatePostInput) => {
      const body = new FormData();
      body.append(
        "request",
        new Blob([JSON.stringify(request)], { type: "application/json" }),
      );
      newPhotos.forEach((file) => body.append("newPhotos", file));
      return apiFetch<PostDetailResponse>(`/api/v1/posts/${id}`, {
        method: "PUT",
        body,
      });
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(["posts", id], saved);
      return queryClient.invalidateQueries({ queryKey: ["posts"] });
    },
  });
}
