import { useMutation } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";
import { clearSharedPhotos } from "@/lib/shared-photos";
import type { PostCreateResponse } from "@/types/api/post";

export function useCreatePost() {
  return useMutation({
    // 화면이 언마운트돼도 정리되도록 전송 직후 여기서 지운다.
    mutationFn: async (body: FormData) => {
      const created = await apiFetch<PostCreateResponse>("/api/v1/posts", {
        method: "POST",
        body,
      });
      try {
        await clearSharedPhotos();
      } catch {
        // 정리 실패는 접수 성공에 영향을 주지 않는다.
      }
      return created;
    },
  });
}
