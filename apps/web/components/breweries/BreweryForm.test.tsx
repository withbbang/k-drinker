import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/breweries/new",
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
      <BreweryForm />
    </QueryProvider>,
  );
}

describe("BreweryForm", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("requires a name", async () => {
    setup();

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(screen.getByText("이름을 입력해 주세요.")).toBeInTheDocument();
    expect(apiFetchMock).not.toHaveBeenCalled();
  });

  it("shows the server field error", async () => {
    apiFetchMock.mockRejectedValue(
      new ApiError(
        "BREWERY_INVALID_INSTAGRAM",
        "인스타그램 계정 형식이 올바르지 않습니다.",
        [{ field: "instagramHandle", reason: "형식이 올바르지 않습니다." }],
      ),
    );
    setup();

    await userEvent.type(screen.getByLabelText("이름"), "국순당");
    await userEvent.type(screen.getByLabelText("인스타그램"), "@@");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("형식이 올바르지 않습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("인스타그램 계정 형식이 올바르지 않습니다."),
    ).toBeInTheDocument();
  });

  it("shows the saved message", async () => {
    apiFetchMock.mockResolvedValue({
      id: 3,
      name: "국순당",
      address: null,
      phone: null,
      homepage: null,
      instagramHandle: null,
      otherSns: null,
      source: "MANUAL",
      verified: false,
    });
    setup();

    await userEvent.type(screen.getByLabelText("이름"), "국순당");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText(
        "저장했습니다. 다음 게시물부터 이 정보가 쓰입니다.",
      ),
    ).toBeInTheDocument();
    const [path, init] = apiFetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/breweries");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({
      name: "국순당",
      address: null,
      phone: null,
      homepage: null,
      instagramHandle: null,
      otherSns: null,
    });
  });
  it("updates the created brewery on the second save", async () => {
    apiFetchMock.mockImplementation(
      async (_path: string, init?: RequestInit) => ({
        id: 3,
        name: "국순당",
        address: null,
        phone: null,
        homepage: null,
        instagramHandle: null,
        otherSns: null,
        source: "MANUAL",
        verified: init?.method === "PUT",
      }),
    );
    setup();

    await userEvent.type(screen.getByLabelText("이름"), "국순당");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByText(
      "저장했습니다. 다음 게시물부터 이 정보가 쓰입니다.",
    );
    await userEvent.type(screen.getByLabelText("전화"), "02-123");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(apiFetchMock).toHaveBeenCalledTimes(2));
    const calls = apiFetchMock.mock.calls.map(([path, init]) => [
      init.method,
      path,
    ]);
    expect(calls).toEqual([
      ["POST", "/api/v1/breweries"],
      ["PUT", "/api/v1/breweries/3"],
    ]);
  });
});
