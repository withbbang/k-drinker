import { render, screen, waitFor } from "@testing-library/react";
import { useQuery } from "@tanstack/react-query";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  QueryProvider,
  loginUrlFor,
  shouldRetry,
} from "@/components/providers/QueryProvider";
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

const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

function AuthFailingQuery() {
  const { isError } = useQuery({
    queryKey: ["auth-failing"],
    queryFn: () =>
      Promise.reject(new ApiError("AUTH_REQUIRED", "로그인이 필요합니다.")),
  });
  return <p>{isError ? "failed" : "loading"}</p>;
}

describe("QueryProvider auth redirect", () => {
  afterEach(() => {
    replaceMock.mockReset();
    window.history.pushState({}, "", "/");
  });

  it("moves to login with the current path as next on AUTH_REQUIRED", async () => {
    // given
    window.history.pushState({}, "", "/posts?x=1");

    // when
    render(
      <QueryProvider>
        <AuthFailingQuery />
      </QueryProvider>,
    );

    // then
    await waitFor(() =>
      expect(replaceMock).toHaveBeenCalledWith("/login?next=%2Fposts%3Fx%3D1"),
    );
  });

  it("does not redirect when already on the login page", async () => {
    // given
    window.history.pushState({}, "", "/login");

    // when
    render(
      <QueryProvider>
        <AuthFailingQuery />
      </QueryProvider>,
    );

    // then
    await screen.findByText("failed");
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
