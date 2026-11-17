import { useMutation } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import type { PostCreateResponse } from "@/types/api/post";

export function useCreatePost() {
  return useMutation({
    mutationFn: (body: FormData) =>
      apiFetch<PostCreateResponse>("/api/v1/posts", {
        method: "POST",
        body,
      }),
  });
}
