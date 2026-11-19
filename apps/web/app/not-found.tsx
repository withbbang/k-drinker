import Link from "next/link";

import { buttonClass } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="mx-auto flex max-w-sm flex-col items-start gap-6 px-4 py-16">
      <h1 className="text-xl font-semibold">찾는 화면이 없어요.</h1>
      <Link href="/" className={buttonClass("secondary")}>
        기록으로 가기
      </Link>
    </main>
  );
}
