import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { LogoutButton } from "@/components/settings/LogoutButton";

const fetchMock = vi.fn();
const { replaceMock } = vi.hoisted(() => ({ replaceMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

function renderButton() {
  render(
    <QueryProvider>
      <LogoutButton />
    </QueryProvider>,
  );
}

// [로그아웃] → 확인 모달의 [로그아웃]
async function confirmLogout() {
  await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(
    within(dialog).getByRole("button", { name: "로그아웃" }),
  );
}

describe("LogoutButton", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
    replaceMock.mockReset();
  });

  it("logs out and moves to the login page", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderButton();

    await confirmLogout();

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
    renderButton();

    await confirmLogout();

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/login"));
    expect(replaceMock).toHaveBeenCalledTimes(1);
  });

  it("로그아웃은_확인해야_한다", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    renderButton();

    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("로그아웃할까요?")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();

    await userEvent.click(
      within(dialog).getByRole("button", { name: "로그아웃" }),
    );
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
    expect(fetchMock.mock.calls[0][0]).toContain("/api/v1/sessions");
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "DELETE" });
  });

  it("로그아웃_확인_모달에서_취소하면_닫힌다", async () => {
    renderButton();

    await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
