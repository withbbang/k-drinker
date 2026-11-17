import { Suspense } from "react";

import { ShareForm } from "@/components/share/ShareForm";

export default function SharePage() {
  return (
    <main className="mx-auto flex w-full max-w-sm flex-col gap-6 px-4 py-10">
      <h1 className="text-2xl font-semibold tracking-tight">후기 보내기</h1>
      <Suspense fallback={null}>
        <ShareForm />
      </Suspense>
    </main>
  );
}
