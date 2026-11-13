"use client";

import { useHealth } from "@/hooks/use-health";

export function HealthStatus() {
  const { data, error, isPending } = useHealth();

  if (isPending) {
    return <p className="text-sm text-neutral-500">확인 중…</p>;
  }
  if (error) {
    return (
      <p role="alert" className="text-sm text-red-700">
        {error.message}
      </p>
    );
  }
  return <p className="text-sm text-neutral-800">API 상태: {data.status}</p>;
}
