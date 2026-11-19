"use client";

import Link from "next/link";

import { buttonClass } from "@/components/ui/button";

export default function ErrorScreen({
  retry,
}: {
  error: Error;
  reset: () => void;
  // 서버 컴포넌트를 다시 받아 그린다. reset은 경계 상태만 지워 같은 오류가 다시 난다.
  retry: () => void;
}) {
  return (
    <main className="mx-auto flex max-w-sm flex-col items-start gap-6 px-4 py-16">
      <h1 className="text-xl font-semibold">화면을 그리지 못했어요.</h1>
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={retry}
          className={buttonClass("primary")}
        >
          다시 시도
        </button>
        <Link href="/" className={buttonClass("text")}>
          기록으로 가기
        </Link>
      </div>
    </main>
  );
}
