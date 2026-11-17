import { describe, expect, it } from "vitest";

import nextConfig from "./next.config";

// 수정 저장(PUT /api/v1/posts/{id})은 Claude 캡션 재생성을 요청 안에서 기다린다.
// 최악: 쓰기·줄이기 2회 × (요청 120초 × 3번 시도 + Retry-After 최대 60초 × 2) = 960초.
const WORST_CAPTION_MS = 2 * (3 * 120_000 + 2 * 60_000);

describe("next.config", () => {
  it("keeps the /api proxy open longer than caption regeneration", () => {
    expect(nextConfig.experimental?.proxyTimeout).toBeGreaterThan(
      WORST_CAPTION_MS,
    );
  });
});
