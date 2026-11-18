import { StrictMode } from "react";

import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/components/providers/QueryProvider";
import { ShareForm } from "@/components/share/ShareForm";
import { ApiError } from "@/lib/api";

const { apiFetchMock, readMock, clearMock, replaceMock } = vi.hoisted(() => ({
  replaceMock: vi.fn(),
  apiFetchMock: vi.fn(),
  readMock: vi.fn(),
  clearMock: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/share",
  useRouter: () => ({ replace: replaceMock }),
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

// [보내기] → 확인 모달의 [보내기]
async function submit() {
  await userEvent.click(screen.getByRole("button", { name: "보내기" }));
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(within(dialog).getByRole("button", { name: "보내기" }));
}

// 사진 N의 [빼기] → 확인 모달의 [빼기]
async function removePhoto(n: number) {
  await userEvent.click(screen.getByRole("button", { name: `사진 ${n} 빼기` }));
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(within(dialog).getByRole("button", { name: "빼기" }));
}

function altOrder() {
  return screen.getAllByRole("img").map((img) => img.getAttribute("alt"));
}

function postCalls() {
  return apiFetchMock.mock.calls.filter(([path]) => path === "/api/v1/posts");
}

describe("ShareForm", () => {
  beforeEach(() => {
    apiFetchMock.mockImplementation(async (path: string) =>
      path === "/api/v1/posts" ? { id: 12, status: "RECEIVED" } : undefined,
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

  it("keeps preview urls alive under StrictMode", async () => {
    // given
    let n = 0;
    URL.createObjectURL = vi.fn(() => `blob:preview-${n++}`);
    readMock.mockResolvedValue(photos(2));

    // when
    render(
      <StrictMode>
        <QueryProvider>
          <ShareForm />
        </QueryProvider>
      </StrictMode>,
    );

    // then
    const imgs = await screen.findAllByRole("img");
    const revoked = vi.mocked(URL.revokeObjectURL).mock.calls.map(([u]) => u);
    imgs.forEach((img) =>
      expect(revoked).not.toContain(img.getAttribute("src")),
    );
  });

  it("still navigates when clearing shared photos fails", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    clearMock.mockRejectedValue(new Error("cache"));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await submit();

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/posts/12"));
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
    await submit();

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
    await submit();

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
    await submit();

    // then
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/posts/12"));
    expect(clearMock).toHaveBeenCalledTimes(1);
  });

  it("keeps send disabled after success so it cannot send twice", async () => {
    // given — 화면 이동이 끝나기 전 상태 (router.replace 모킹)
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await submit();
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/posts/12"));

    // then
    const sendButtons = screen.getAllByRole("button", { name: "보내기" });
    sendButtons.forEach((button) => expect(button).toBeDisabled());
    await userEvent.click(sendButtons[0]);
    expect(postCalls()).toHaveLength(1);
  });

  it("closes the modal and shows the error when sending fails", async () => {
    // given
    apiFetchMock.mockImplementation(async (path: string) => {
      if (path === "/api/v1/posts") {
        throw new ApiError("COMMON_VALIDATION_FAILED", "사진을 확인해 주세요.");
      }
      return undefined;
    });
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await submit();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "사진을 확인해 주세요.",
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "보내기" })).toBeEnabled();
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
    await submit();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "저장에 실패했습니다.",
    );
    expect(clearMock).not.toHaveBeenCalled();
  });

  it("asks for confirmation before sending", async () => {
    // given
    readMock.mockResolvedValue(photos(3));
    renderForm();
    await screen.findAllByRole("img");
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "별점" }),
      "4.5",
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: "메모" }),
      "가".repeat(45),
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("사진 3장");
    expect(dialog).toHaveTextContent("4.5");
    expect(dialog).toHaveTextContent("가".repeat(40) + "…");
    expect(dialog).not.toHaveTextContent("가".repeat(41));
    expect(postCalls()).toHaveLength(0);
  });

  it("shows 없음 for empty rating and memo", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));

    // then
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getAllByText("없음")).toHaveLength(2);
  });

  it("sends nothing when going back from the modal", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");
    await userEvent.click(screen.getByRole("button", { name: "보내기" }));
    const dialog = await screen.findByRole("dialog");

    // when
    await userEvent.click(
      within(dialog).getByRole("button", { name: "돌아가기" }),
    );

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(postCalls()).toHaveLength(0);
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("blocks eleven photos until one is removed", async () => {
    // given
    readMock.mockResolvedValue(photos(11));

    // when
    renderForm();

    // then
    const blocked = await screen.findByRole("button", {
      name: "사진을 10장 이하로 줄여 주세요 (지금 11장)",
    });
    expect(blocked).toBeDisabled();
    expect(screen.getByRole("textbox", { name: "메모" })).toBeDisabled();
    expect(screen.getByRole("combobox", { name: "별점" })).toBeDisabled();
    expect(screen.queryByText(/1장 이상 10장 이하/)).toBeNull();

    // when
    await removePhoto(1);

    // then
    expect(screen.getByRole("button", { name: "보내기" })).toBeEnabled();
    expect(screen.getByRole("textbox", { name: "메모" })).toBeEnabled();
    expect(screen.getByRole("combobox", { name: "별점" })).toBeEnabled();
  });

  it("disables remove on the last photo", async () => {
    // given
    readMock.mockResolvedValue(photos(1));

    // when
    renderForm();

    // then
    await screen.findAllByRole("img");
    expect(screen.getByRole("button", { name: /빼기/ })).toBeDisabled();
  });

  it("does not send removed photos", async () => {
    // given
    readMock.mockResolvedValue(photos(3));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await removePhoto(1);
    await submit();

    // then
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const names = (postCalls()[0][1].body as FormData)
      .getAll("photos")
      .map((f) => (f as File).name);
    expect(names).toEqual(["p1.jpg", "p2.jpg"]);
  });

  it("removes a photo only after confirmation", async () => {
    // given
    readMock.mockResolvedValue(photos(3));
    renderForm();
    await screen.findAllByRole("img");

    // when — 취소
    await userEvent.click(screen.getByRole("button", { name: "사진 2 빼기" }));
    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("이 사진을 뺄까요?");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getAllByRole("img")).toHaveLength(3);

    // when — 확인
    await removePhoto(2);

    // then
    expect(screen.getAllByRole("img")).toHaveLength(2);
  });

  it("restores a removed photo to its place on undo and redo", async () => {
    // given
    readMock.mockResolvedValue(photos(3));
    renderForm();
    await screen.findAllByRole("img");
    const original = altOrder();
    await removePhoto(2);
    expect(screen.getAllByRole("img")).toHaveLength(2);

    // when
    await userEvent.click(screen.getByRole("button", { name: "되돌리기" }));

    // then
    expect(altOrder()).toEqual(original);

    // when
    await userEvent.click(screen.getByRole("button", { name: "다시 하기" }));

    // then
    expect(screen.getAllByRole("img")).toHaveLength(2);
  });

  it("undoes the rating too", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "별점" }),
      "4.0",
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "되돌리기" }));

    // then
    expect(screen.getByRole("combobox", { name: "별점" })).toHaveValue("");
  });

  it("does not record memo changes for undo", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    renderForm();
    await screen.findAllByRole("img");

    // when
    await userEvent.type(screen.getByRole("textbox", { name: "메모" }), "좋음");

    // then
    expect(screen.getByRole("button", { name: "되돌리기" })).toBeDisabled();
  });

  it("appends added photos and notes the overflow", async () => {
    // given
    readMock.mockResolvedValue(photos(9));
    renderForm();
    await screen.findAllByRole("img");

    // when
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]')!;
    await userEvent.upload(input, [
      photo("a.jpg"),
      photo("b.jpg"),
      photo("c.jpg"),
    ]);

    // then
    expect(screen.getAllByRole("img")).toHaveLength(10);
    expect(screen.getByRole("status")).toHaveTextContent(
      "사진은 10장까지 넣을 수 있어 2장은 넣지 않았습니다.",
    );
    expect(screen.queryByRole("button", { name: "사진 추가" })).toBeNull();

    // when — 다음 조작에서 안내가 사라진다
    await userEvent.click(screen.getByRole("button", { name: "되돌리기" }));

    // then
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("sends added photos in order after the shared ones", async () => {
    // given
    readMock.mockResolvedValue(photos(2));
    renderForm();
    await screen.findAllByRole("img");
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]')!;
    await userEvent.upload(input, photo("added.jpg"));

    // when
    await submit();

    // then
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const names = (postCalls()[0][1].body as FormData)
      .getAll("photos")
      .map((f) => (f as File).name);
    expect(names).toEqual(["p0.jpg", "p1.jpg", "added.jpg"]);
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

  it("shows guidance when reading shared photos fails", async () => {
    // given
    readMock.mockRejectedValue(new Error("cache"));

    // when
    renderForm();

    // then
    expect(
      await screen.findByText(/공유된 사진이 없습니다/),
    ).toBeInTheDocument();
  });

  it("shows a retry message when the session check fails", async () => {
    // given
    readMock.mockResolvedValue(photos(1));
    apiFetchMock.mockRejectedValue(
      new ApiError("COMMON_NOT_FOUND", "찾을 수 없습니다."),
    );

    // when
    renderForm();

    // then
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });
});
