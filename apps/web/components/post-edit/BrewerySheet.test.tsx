import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BrewerySheet } from "@/components/post-edit/BrewerySheet";
import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts/7/edit",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

const brewery: BreweryResponse = {
  id: 5,
  name: "국순당",
  address: "서울",
  phone: null,
  homepage: null,
  instagramHandle: null,
  otherSns: null,
  source: "MANUAL",
  verified: false,
};

function setup() {
  const onClose = vi.fn();
  render(
    <QueryProvider>
      <button type="button">양조장 정보 고치기</button>
      <BrewerySheet brewery={brewery} onClose={onClose} />
    </QueryProvider>,
  );
  return { onClose };
}

describe("BrewerySheet", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("opens as a dialog with the notice and focuses the first input", () => {
    setup();

    expect(screen.getByRole("dialog", { name: "양조장 정보" })).toHaveAttribute(
      "aria-modal",
      "true",
    );
    expect(
      screen.getByText("이 양조장을 쓰는 다른 게시물에도 반영돼요"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("이름")).toHaveFocus();
  });

  it("closes after a successful save", async () => {
    apiFetchMock.mockResolvedValue({ ...brewery, phone: "02-1" });
    const { onClose } = setup();

    await userEvent.type(screen.getByLabelText("전화"), "02-1");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1));
    const [path, init] = apiFetchMock.mock.calls[0];
    expect(path).toBe("/api/v1/breweries/5");
    expect(init.method).toBe("PUT");
  });

  it("keeps the sheet open and shows field errors on a 400", async () => {
    apiFetchMock.mockRejectedValue(
      new ApiError("COMMON_VALIDATION_FAILED", "입력값이 올바르지 않습니다.", [
        { field: "name", reason: "이름을 확인해 주세요." },
      ]),
    );
    const { onClose } = setup();

    await userEvent.clear(screen.getByLabelText("이름"));
    await userEvent.type(screen.getByLabelText("이름"), "(주)");
    await userEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(
      await screen.findByText("이름을 확인해 주세요."),
    ).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("closes on Esc", () => {
    const { onClose } = setup();

    fireEvent(
      screen.getByRole("dialog"),
      new Event("cancel", { cancelable: true }),
    );

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("closes with the close button", async () => {
    const { onClose } = setup();

    await userEvent.click(screen.getByRole("button", { name: "닫기" }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("returns focus to the opener when unmounted", () => {
    const opener = document.createElement("button");
    document.body.append(opener);
    opener.focus();
    const { unmount } = render(
      <QueryProvider>
        <BrewerySheet brewery={brewery} onClose={vi.fn()} />
      </QueryProvider>,
    );
    expect(opener).not.toHaveFocus();

    unmount();

    expect(opener).toHaveFocus();
    opener.remove();
  });
});
