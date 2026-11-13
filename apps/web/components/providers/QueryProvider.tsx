"use client";

import { useState, type ReactNode } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";

const MAX_QUERY_RETRIES = 3;
const NO_RETRY_CODES = new Set(["AUTH_REQUIRED", "COMMON_NOT_FOUND"]);

function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && NO_RETRY_CODES.has(error.code)) {
    return false;
  }
  return failureCount < MAX_QUERY_RETRIES;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: shouldRetry } },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
