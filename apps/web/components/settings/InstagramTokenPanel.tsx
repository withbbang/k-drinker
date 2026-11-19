"use client";

import {
  useInstagramToken,
  useReloadInstagramToken,
} from "@/hooks/use-instagram-token";
import { buttonClass } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";

export function InstagramTokenPanel() {
  const token = useInstagramToken();
  const reload = useReloadInstagramToken();

  return (
    <section className="flex flex-col gap-3 rounded-field border border-line bg-surface p-4">
      <h2 className="text-lg font-medium">인스타그램 연결</h2>
      {token.isPending && <p className="text-sm text-muted">불러오는 중…</p>}
      {token.isError && (
        <p role="alert" className="text-sm text-danger">
          {token.error instanceof ApiError
            ? token.error.message
            : "토큰 상태를 불러오지 못했어요."}
        </p>
      )}
      {token.data && (
        <p className="text-sm text-muted">
          마지막 반영:{" "}
          {token.data.refreshedAt
            ? formatDateTime(token.data.refreshedAt)
            : "없음"}
        </p>
      )}
      <button
        type="button"
        disabled={reload.isPending}
        onClick={() => reload.mutate()}
        className={buttonClass("secondary", "w-full")}
      >
        토큰 다시 불러오기
      </button>
      {reload.isSuccess && (
        <p role="status" className="text-sm text-accent">
          인스타그램 토큰을 다시 불러왔어요.
        </p>
      )}
      {reload.isError && (
        <p role="alert" className="text-sm text-danger">
          {reload.error instanceof ApiError
            ? reload.error.message
            : "토큰을 다시 불러오지 못했어요."}
        </p>
      )}
    </section>
  );
}
