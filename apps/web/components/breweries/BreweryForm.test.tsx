import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BreweryForm } from "@/components/breweries/BreweryForm";
import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";

const { apiFetchMock, replaceMock } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
  replaceMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/breweries/new",
  useRouter: () => ({ replace: replaceMock }),
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

const BREWERY = {
  id: 3,
  name: "국순당",
  address: null,
  phone: null,
  homepage: null,
  instagramHandle: null,
  otherSns: null,
  source: "MANUAL",
  verified: false,
};

describe("BreweryForm", () => {
  it("새로_만든_뒤_옮겨_온_화면이면_저장_완료를_보인다", () => {
    // given
    render(
      <QueryProvider>
        <BreweryForm brewery={BREWERY} justCreated />
      </QueryProvider>,
    );

    // then
    expect(
      screen.getByText("저장했어요. 다음 게시물부터 이 정보가 쓰여요."),
    ).toBeInTheDocument();
  });

  beforeEach(() => {
    apiFetchMock.mockReset();
    replaceMock.mockReset();
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
      await screen.findByText("저장했어요. 다음 게시물부터 이 정보가 쓰여요."),
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
    await screen.findByText("저장했어요. 다음 게시물부터 이 정보가 쓰여요.");
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

  it("calls onSaved with the saved brewery", async () => {
    const saved = {
      id: 3,
      name: "국순당",
      address: null,
      phone: null,
      homepage: null,
      instagramHandle: null,
      otherSns: null,
      source: "MANUAL",
      verified: true,
    };
    apiFetchMock.mockResolvedValue(saved);
    const onSaved = vi.fn();
    render(
      <QueryProvider>
        <BreweryForm onSaved={onSaved} />
      </QueryProvider>,
    );

    await userEvent.type(screen.getByLabelText("이름"), "국순당");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
    expect(
      screen.queryByText("저장했어요. 다음 게시물부터 이 정보가 쓰여요."),
    ).toBeNull();
  });

  it("입력칸은_어두운_토큰_색을_쓴다", () => {
    setup();
    for (const input of screen.getAllByRole("textbox")) {
      expect(input).not.toHaveClass("bg-white");
      expect(input).toHaveClass("bg-surface", "text-text");
    }
  });

  it("칸마다_맞는_키보드", () => {
    setup();
    expect(screen.getByLabelText("전화")).toHaveAttribute("type", "tel");
    expect(screen.getByLabelText("홈페이지")).toHaveAttribute("type", "url");
    expect(screen.getByLabelText("인스타그램")).toHaveAttribute(
      "placeholder",
      "@ 없이 아이디만",
    );
  });

  it("새로_만들면_상세_주소로_옮긴다", async () => {
    apiFetchMock.mockResolvedValue({ ...BREWERY, id: 7 });
    setup();

    await userEvent.type(screen.getByLabelText("이름"), "국순당");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledTimes(1));
    expect(replaceMock).toHaveBeenCalledWith("/breweries/7?saved=1");
  });

  it("있는_양조장을_저장하면_머문다", async () => {
    apiFetchMock.mockResolvedValue(BREWERY);
    render(
      <QueryProvider>
        <BreweryForm brewery={BREWERY} />
      </QueryProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("저장했어요. 다음 게시물부터 이 정보가 쓰여요."),
    ).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });
});
