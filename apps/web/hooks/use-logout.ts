import { useRouter } from "next/navigation";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

// 로그아웃. 성공·실패와 관계없이 캐시를 비우고 로그인 화면으로 보낸다.
export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  return useMutation({
    // 실패(이미 만료된 401 등)를 던지면 QueryProvider가 next가 붙은 로그인 주소로
    // 또 보내므로 여기서 삼킨다.
    mutationFn: () =>
      apiFetch<void>("/api/v1/sessions", { method: "DELETE" }).catch(
        () => undefined,
      ),
    onSettled: () => {
      queryClient.clear();
      router.replace("/login");
    },
  });
}
