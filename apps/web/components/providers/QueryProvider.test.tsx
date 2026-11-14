import { describe, expect, it } from "vitest";

import { loginUrlFor, shouldRetry } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";

describe("shouldRetry", () => {
  it("does not retry validation failures", () => {
    // given
    const error = new ApiError(
      "COMMON_VALIDATION_FAILED",
      "요청 값이 올바르지 않습니다.",
    );

    // when
    const retry = shouldRetry(0, error);

    // then
    expect(retry).toBe(false);
  });

  it("retries server errors up to 3 times", () => {
    // given
    const error = new ApiError("COMMON_INTERNAL_ERROR", "서버 오류");

    // when
    const retries = [0, 1, 2, 3].map((count) => shouldRetry(count, error));

    // then
    expect(retries).toEqual([true, true, true, false]);
  });
});

describe("loginUrlFor", () => {
  it("carries the current path and query as next", () => {
    expect(loginUrlFor("/share", "?id=1")).toBe(
      "/login?next=%2Fshare%3Fid%3D1",
    );
  });

  it("omits next for the home path", () => {
    expect(loginUrlFor("/", "")).toBe("/login");
  });
});
