import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { AppNav } from "@/components/layout/AppNav";
import { QueryProvider } from "@/components/providers/QueryProvider";

const fetchMock = vi.fn();
const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/breweries",
  useRouter: () => ({ replace: replaceMock }),
}));

function renderNav() {
  render(
    <QueryProvider>
      <AppNav />
    </QueryProvider>,
  );
}

describe("AppNav", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replaceMock.mockReset();
  });

  it("marks the current page", () => {
    renderNav();

    expect(screen.getByRole("link", { name: "양조장" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("link", { name: "기록" })).not.toHaveAttribute(
      "aria-current",
    );
    expect(screen.getByRole("link", { name: "설정" })).toHaveAttribute(
      "href",
      "/settings",
    );
  });

  it("logs out and moves to the login page", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderNav();

    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toContain("/api/v1/sessions");
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "DELETE" });
    expect(replaceMock).toHaveBeenCalledTimes(1);
  });

  it("moves to the login page even when logout fails", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          code: "AUTH_REQUIRED",
          message: "로그인이 필요합니다",
        }),
        { status: 401, headers: { "Content-Type": "application/json" } },
      ),
    );
    renderNav();

    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
    expect(replaceMock).toHaveBeenCalledTimes(1);
  });
});
