"use client";

import { useState, type ReactNode } from "react";

import { useRouter } from "next/navigation";

import {
  MutationCache,
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";

import { ApiError } from "@/lib/api";

const MAX_QUERY_RETRIES = 3;
const LOGIN_PATH = "/login";
// 다시 보내도 결과가 같은 오류는 재시도하지 않는다.
const NO_RETRY_CODES = new Set([
  "AUTH_REQUIRED",
  "COMMON_NOT_FOUND",
  "COMMON_VALIDATION_FAILED",
]);

export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && NO_RETRY_CODES.has(error.code)) {
    return false;
  }
  return failureCount < MAX_QUERY_RETRIES;
}

// 로그인 후 돌아올 수 있게 현재 경로를 next로 싣는다. 홈은 생략한다.
export function loginUrlFor(pathname: string, search: string): string {
  const current = `${pathname}${search}`;
  if (current === "/") {
    return LOGIN_PATH;
  }
  return `${LOGIN_PATH}?next=${encodeURIComponent(current)}`;
}

function createClient(moveToLogin: () => void) {
  const redirectIfAuthRequired = (error: unknown) => {
    if (
      !(error instanceof ApiError) ||
      error.code !== "AUTH_REQUIRED" ||
      window.location.pathname === LOGIN_PATH
    ) {
      return;
    }
    moveToLogin();
  };

  return new QueryClient({
    queryCache: new QueryCache({ onError: redirectIfAuthRequired }),
    mutationCache: new MutationCache({ onError: redirectIfAuthRequired }),
    defaultOptions: { queries: { retry: shouldRetry } },
  });
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [queryClient] = useState(() =>
    createClient(() =>
      router.replace(
        loginUrlFor(window.location.pathname, window.location.search),
      ),
    ),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
