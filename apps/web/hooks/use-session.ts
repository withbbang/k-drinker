import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

// 로그인 여부 확인. 401이면 QueryProvider가 로그인 화면으로 보낸다.
export function useSession() {
  return useQuery({
    queryKey: ["session"],
    // 204 본문 없음(undefined)은 React Query가 오류로 보므로 true로 바꾼다.
    queryFn: async () => {
      await apiFetch<void>("/api/v1/sessions/current");
      return true;
    },
  });
}
