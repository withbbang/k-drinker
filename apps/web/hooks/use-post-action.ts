import { useMutation, useQueryClient } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

export type PostAction = "confirm" | "cancel" | "retry";

// 상태가 바뀐 경우(409)와 캡션 초과로 실패 처리된 경우(400)는 서버 상태를 다시 읽는다.
const RELOAD_CODES = [
  "POST_VERSION_CONFLICT",
  "POST_NOT_EDITABLE",
  "POST_CAPTION_TOO_LONG",
];

export function usePostAction(id: string, action: PostAction) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (version: number) =>
      apiFetch<PostDetailResponse>(`/api/v1/posts/${id}/${action}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ version }),
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(["posts", id], saved);
      return queryClient.invalidateQueries({ queryKey: ["posts"] });
    },
    onError: (error) => {
      if (error instanceof ApiError && RELOAD_CODES.includes(error.code)) {
        return queryClient.invalidateQueries({ queryKey: ["posts", id] });
      }
    },
  });
}
