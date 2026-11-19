import { Suspense } from "react";

import { ShareForm } from "@/components/share/ShareForm";

export default function SharePage() {
  return (
    // 제목·보내기는 ShareForm의 상단 바가 맡는다.
    <main className="mx-auto flex w-full max-w-sm flex-col">
      <Suspense fallback={null}>
        <ShareForm />
      </Suspense>
    </main>
  );
}
