"use client";

import { useEffect } from "react";

export function ServiceWorkerRegister() {
  useEffect(() => {
    navigator.serviceWorker?.register("/sw.js", { scope: "/" }).catch(() => {
      // 등록 실패는 공유 기능만 못 쓰는 것이라 무시한다.
    });
  }, []);

  return null;
}
