"use client";

import { useState, type FormEvent } from "react";

import { useRouter, useSearchParams } from "next/navigation";

import { useMutation } from "@tanstack/react-query";

import { ApiError, apiFetch } from "@/lib/api";
import { safeNext } from "@/lib/safe-next";
import type { SessionCreateRequest } from "@/types/api/auth";

const CODE_LENGTH = 6;
const FALLBACK_ERROR = "로그인에 실패했습니다. 잠시 후 다시 시도해 주세요.";

function createSession(request: SessionCreateRequest): Promise<void> {
  return apiFetch<void>("/api/v1/sessions", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
}

export function LoginForm() {
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const [code, setCode] = useState("");
  const login = useMutation({
    mutationFn: createSession,
    onSuccess: () => router.replace(next),
  });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    login.mutate({ code });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">인증 코드</span>
        <input
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          value={code}
          onChange={(event) =>
            setCode(event.target.value.replace(/\D/g, "").slice(0, CODE_LENGTH))
          }
          className="rounded-md border border-neutral-300 bg-white px-3 py-2.5 text-center text-2xl font-semibold tracking-[0.5em] tabular-nums focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        />
      </label>
      <button
        type="submit"
        disabled={code.length !== CODE_LENGTH || login.isPending}
        className="rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
      >
        로그인
      </button>
      {login.isError && (
        <p role="alert" className="text-sm text-red-700">
          {login.error instanceof ApiError
            ? login.error.message
            : FALLBACK_ERROR}
        </p>
      )}
    </form>
  );
}
