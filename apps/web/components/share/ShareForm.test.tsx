import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { ShareForm } from "@/components/share/ShareForm";
import { ApiError } from "@/lib/api";

const { apiFetchMock, readMock, clearMock } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
  readMock: vi.fn(),
  clearMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/share",
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));
vi.mock("@/lib/shared-photos", () => ({
  readSharedPhotos: readMock,
  clearSharedPhotos: clearMock,
}));

function photo(name: string) {
  return new File(["x"], name, { type: "image/jpeg" });
}

function photos(count: number) {
  return Array.from({ length: count }, (_, i) => photo(`p${i}.jpg`));
}

function renderForm() {
  render(
    <QueryProvider>
      <ShareForm />
    </QueryProvider>,
  );
}

function postCalls() {
  return apiFetchMock.mock.calls.filter(([path]) => path === "/api/v1/posts");
}

describe("ShareForm", () => {
  beforeEach(() => {
    apiFetchMock.mockImplementation(async (path: string) =>
      path === "/api/v1/posts" ? { id: 1, status: "RECEIVED" } : undefined,
    );
    clearMock.mockResolvedValue(undefined);
    URL.createObjectURL = vi.fn(() => "blob:preview");
    URL.revokeObjectURL = vi.fn();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows shared photo previews", async () => {
    // given
    readMock.mockResolvedValue(photos(2));

    // when
    renderForm();

    // then
    expect(await screen.findAllByRole("img")).toHaveLength(2);
  });

  it("sends photos memo rating and SHARE source", async () => {
    // given
    readMock.mockResolvedValue(photos(2));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.type(screen.getByRole("textbox", { name: "메모" }), "좋음");
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "별점" }),
      "4.5",
    );
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const [, init] = postCalls()[0];
    expect(init.method).toBe("POST");
    const body = init.body as FormData;
    expect(body.getAll("photos")).toHaveLength(2);
    expect(body.get("memo")).toBe("좋음");
    expect(body.get("rating")).toBe("4.5");
    expect(body.get("source")).toBe("SHARE");
  });

  it("omits rating when none is chosen", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const body = postCalls()[0][1].body as FormData;
    expect(body.has("rating")).toBe(false);
  });

  it("clears shared photos after sending", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    expect(
      await screen.findByText(
        "접수했습니다. 분석이 끝나면 텔레그램으로 개요를 보내 드립니다.",
      ),
    ).toBeInTheDocument();
    expect(clearMock).toHaveBeenCalledTimes(1);
  });

  it("keeps shared photos until sent", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    apiFetchMock.mockImplementation(async (path: string) => {
      if (path === "/api/v1/posts") {
        throw new ApiError("POST_PHOTO_COUNT", "저장에 실패했습니다.");
      }
    });
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "저장에 실패했습니다.",
    );
    expect(clearMock).not.toHaveBeenCalled();
  });

  it("blocks sending more than ten photos", async () => {
    // given
    readMock.mockResolvedValue(photos(11));

    // when
    renderForm();

    // then
    expect(
      await screen.findByText("사진은 1장 이상 10장 이하로 올려 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
  });

  it("shows guidance when nothing was shared", async () => {
    // given
    readMock.mockResolvedValue([]);

    // when
    renderForm();

    // then
    expect(
      await screen.findByText(
        "공유된 사진이 없습니다. 갤러리에서 사진을 골라 k-drinker로 공유해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "보내기" })).toBeNull();
  });
});
