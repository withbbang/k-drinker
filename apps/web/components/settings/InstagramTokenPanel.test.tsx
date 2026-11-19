import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { InstagramTokenPanel } from "@/components/settings/InstagramTokenPanel";
import { ApiError } from "@/lib/api";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/settings",
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

function setup() {
  render(
    <QueryProvider>
      <InstagramTokenPanel />
    </QueryProvider>,
  );
}

describe("InstagramTokenPanel", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("reloads the token and shows the time", async () => {
    apiFetchMock.mockImplementation(async (path: string) =>
      path.endsWith("/reload")
        ? {
            expiresAt: "2027-01-17T00:00:00Z",
            refreshedAt: "2026-11-18T03:00:00Z",
          }
        : { expiresAt: null, refreshedAt: null },
    );
    setup();
    expect(await screen.findByText("마지막 반영: 없음")).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole("button", { name: "토큰 다시 불러오기" }),
    );

    expect(
      await screen.findByText("인스타그램 토큰을 다시 불러왔어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("마지막 반영: 2026.11.18 12:00"),
    ).toBeInTheDocument();
    expect(apiFetchMock).toHaveBeenCalledWith(
      "/api/v1/instagram/token/reload",
      { method: "POST" },
    );
  });

  it("shows the missing token message", async () => {
    apiFetchMock.mockImplementation(async (path: string) => {
      if (path.endsWith("/reload")) {
        throw new ApiError(
          "INSTAGRAM_TOKEN_MISSING",
          "INSTAGRAM_ACCESS_TOKEN 환경변수가 비어 있습니다.",
        );
      }
      return { expiresAt: null, refreshedAt: null };
    });
    setup();

    await userEvent.click(
      await screen.findByRole("button", { name: "토큰 다시 불러오기" }),
    );

    expect(
      await screen.findByText(
        "INSTAGRAM_ACCESS_TOKEN 환경변수가 비어 있습니다.",
      ),
    ).toBeInTheDocument();
  });
});
