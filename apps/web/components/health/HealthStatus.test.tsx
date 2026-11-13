import type { ReactNode } from "react";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { HealthStatus } from "@/components/health/HealthStatus";

function renderWithQuery(ui: ReactNode) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("HealthStatus", () => {
  it("shows the API status when the server is up", async () => {
    // given
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ success: true, data: { status: "UP" } }),
            { status: 200 },
          ),
        ),
    );

    // when
    renderWithQuery(<HealthStatus />);

    // then
    expect(await screen.findByText("API 상태: UP")).toBeInTheDocument();
  });

  it("shows an error message when the request fails", async () => {
    // given
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("<html>Bad Gateway</html>", { status: 502 }),
        ),
    );

    // when
    renderWithQuery(<HealthStatus />);

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    );
  });
});
