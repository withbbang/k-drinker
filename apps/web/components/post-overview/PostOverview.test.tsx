import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { PostOverview } from "@/components/post-overview/PostOverview";
import { QueryProvider } from "@/components/providers/QueryProvider";
import { ApiError } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts/7",
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

const brewery = {
  id: 5,
  name: "국순당",
  address: "서울 강남구",
  phone: null,
  homepage: null,
  instagramHandle: null,
  otherSns: null,
  source: "MANUAL",
  verified: false,
};

function post(overrides: Partial<PostDetailResponse> = {}): PostDetailResponse {
  return {
    id: 7,
    status: "PENDING_REVIEW",
    version: 4,
    editable: true,
    failedStage: null,
    failureReason: null,
    drinkId: 9,
    drinkName: "막걸리",
    brewery,
    rating: 4.5,
    memo: "좋음",
    captionHead: "머리 문장",
    breweryBlock: "국순당\n서울 강남구",
    captionTail: "#막걸리",
    captionEdited: false,
    captionLength: 123,
    photos: [
      { id: 1, position: 0, url: "/m/1.jpg", fitMode: "CROP" },
      { id: 2, position: 1, url: "/m/2.jpg", fitMode: "CROP" },
    ],
    permalink: null,
    channel: "WEB",
    ...overrides,
  };
}

function setup(detail: PostDetailResponse) {
  apiFetchMock.mockImplementation(async (path: string, init?: RequestInit) => {
    if (init?.method === "POST") return { ...detail, status: "PUBLISHING" };
    return detail;
  });
  render(
    <QueryProvider>
      <PostOverview id="7" />
    </QueryProvider>,
  );
}

function postCalls() {
  return apiFetchMock.mock.calls.filter(([, init]) => init?.method === "POST");
}

describe("PostOverview", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });
  afterEach(() => vi.restoreAllMocks());

  it("shows an analyzing message without buttons", async () => {
    setup(post({ status: "ANALYZING" }));
    expect(await screen.findByText("분석 중입니다")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows a publishing message without buttons", async () => {
    setup(post({ status: "PUBLISHING" }));
    expect(await screen.findByText("게시 중입니다")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows the review with photos, caption and actions for a web post", async () => {
    setup(post());
    expect(await screen.findByText("막걸리")).toBeInTheDocument();
    const imgs = screen.getAllByRole("img");
    expect(imgs).toHaveLength(2);
    expect(imgs[0]).toHaveAttribute("alt", "사진 1");
    expect(imgs[1]).toHaveAttribute("alt", "사진 2");
    expect(imgs[0]).toHaveAttribute("src", "/m/1.jpg?v=4");
    expect(screen.getAllByText(/서울 강남구/).length).toBeGreaterThan(0);
    expect(screen.getByText(/4\.5/)).toBeInTheDocument();
    expect(screen.getByText(/머리 문장/)).toBeInTheDocument();
    expect(screen.getByText(/#막걸리/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "확인" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "취소" })).toBeEnabled();
    expect(screen.getByRole("link", { name: "수정" })).toHaveAttribute(
      "href",
      "/posts/7/edit",
    );
  });

  it("posts confirm with the version after the user agrees", async () => {
    setup(post());
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));

    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const [path, init] = postCalls()[0];
    expect(path).toBe("/api/v1/posts/7/confirm");
    expect(JSON.parse(init.body as string)).toEqual({ version: 4 });
  });

  it("does not post when the user declines the confirm dialog", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    setup(post());
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));

    expect(postCalls()).toHaveLength(0);
  });

  it("disables all buttons while an action is in flight", async () => {
    let release: (v: PostDetailResponse) => void = () => {};
    apiFetchMock.mockImplementation(async (_p: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        return new Promise<PostDetailResponse>((r) => (release = r));
      }
      return post();
    });
    render(
      <QueryProvider>
        <PostOverview id="7" />
      </QueryProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "확인" })).toBeDisabled(),
    );
    expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
    release(post({ status: "PUBLISHING" }));
  });

  it("shows the conflict message and reloads the post", async () => {
    apiFetchMock.mockImplementation(async (_p: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        throw new ApiError(
          "POST_VERSION_CONFLICT",
          "이미 상태가 바뀌었습니다.",
        );
      }
      return post();
    });
    render(
      <QueryProvider>
        <PostOverview id="7" />
      </QueryProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이미 상태가 바뀌었습니다.",
    );
    await waitFor(() => {
      const gets = apiFetchMock.mock.calls.filter(
        ([p, init]) => p === "/api/v1/posts/7" && !init?.method,
      );
      expect(gets.length).toBeGreaterThanOrEqual(2);
    });
  });

  it("reloads the post when the caption is too long", async () => {
    apiFetchMock.mockImplementation(async (_p: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        throw new ApiError("POST_CAPTION_TOO_LONG", "캡션이 너무 깁니다.");
      }
      return post();
    });
    render(
      <QueryProvider>
        <PostOverview id="7" />
      </QueryProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "캡션이 너무 깁니다.",
    );
    await waitFor(() => {
      const gets = apiFetchMock.mock.calls.filter(
        ([p, init]) => p === "/api/v1/posts/7" && !init?.method,
      );
      expect(gets.length).toBeGreaterThanOrEqual(2);
    });
  });

  it("shows a telegram notice without confirm and cancel", async () => {
    setup(post({ channel: "TELEGRAM" }));
    expect(
      await screen.findByText("텔레그램에서 진행 중인 게시물입니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "확인" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "취소" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "수정" })).toBeInTheDocument();
  });

  it("hides edit for a telegram post that is not editable", async () => {
    setup(post({ channel: "TELEGRAM", editable: false }));
    await screen.findByText("텔레그램에서 진행 중인 게시물입니다.");
    expect(
      screen.queryByRole("link", { name: "수정" }),
    ).not.toBeInTheDocument();
  });

  it("links to the permalink when published", async () => {
    setup(
      post({ status: "PUBLISHED", permalink: "https://instagram.com/p/abc" }),
    );
    expect(
      await screen.findByRole("link", { name: /인스타그램/ }),
    ).toHaveAttribute("href", "https://instagram.com/p/abc");
  });

  it("shows the failure reason with retry and edit when failed", async () => {
    setup(post({ status: "FAILED", failureReason: "게시에 실패했습니다" }));
    expect(await screen.findByText("게시에 실패했습니다")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    expect(postCalls()[0][0]).toBe("/api/v1/posts/7/retry");
    expect(screen.getByRole("link", { name: "수정" })).toBeInTheDocument();
  });

  it("shows a canceled message", async () => {
    setup(post({ status: "CANCELED" }));
    expect(await screen.findByText("취소한 게시물입니다")).toBeInTheDocument();
  });
});
