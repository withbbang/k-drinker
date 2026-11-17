import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

// 로그인 여부 확인. 401이면 QueryProvider가 로그인 화면으로 보낸다.
export function useSession() {
  return useQuery({
    queryKey: ["session"],
    queryFn: () => apiFetch<void>("/api/v1/sessions/current"),
  });
}
