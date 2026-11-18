import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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
    publishCaption: "머리 문장\n\n국순당\n서울 강남구\n\n#막걸리",
    retryable: false,
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

// 화면 버튼을 눌러 확인 모달을 열고 모달의 버튼으로 진행한다.
async function act(pageLabel: string, dialogLabel: string) {
  await userEvent.click(await screen.findByRole("button", { name: pageLabel }));
  const dialog = await screen.findByRole("dialog");
  await userEvent.click(
    within(dialog).getByRole("button", { name: dialogLabel }),
  );
}

function postCalls() {
  return apiFetchMock.mock.calls.filter(([, init]) => init?.method === "POST");
}

describe("PostOverview", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
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
    const dialog = await screen.findByRole("dialog");
    expect(
      within(dialog).getByText("인스타그램에 게시할까요?"),
    ).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "확인" }));

    await waitFor(() => expect(postCalls()).toHaveLength(1));
    const [path, init] = postCalls()[0];
    expect(path).toBe("/api/v1/posts/7/confirm");
    expect(JSON.parse(init.body as string)).toEqual({ version: 4 });
  });

  it("closes the confirm dialog when the post version changes", async () => {
    let current = post();
    apiFetchMock.mockImplementation(async () => current);
    const client = new QueryClient();
    render(
      <QueryClientProvider client={client}>
        <PostOverview id="7" />
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    current = post({ version: 5 });
    await client.invalidateQueries({ queryKey: ["posts", "7"] });

    await waitFor(() =>
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument(),
    );
    expect(postCalls()).toHaveLength(0);
  });

  it("closes the confirm dialog when the post status changes", async () => {
    let current = post();
    apiFetchMock.mockImplementation(async () => current);
    const client = new QueryClient();
    render(
      <QueryClientProvider client={client}>
        <PostOverview id="7" />
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    current = post({ status: "PUBLISHING", version: 5 });
    await client.invalidateQueries({ queryKey: ["posts", "7"] });

    expect(await screen.findByText("게시 중입니다")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("does not post when the user declines the confirm dialog", async () => {
    setup(post());
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(postCalls()).toHaveLength(0);
  });

  it("게시_확인_모달_배경_클릭이면_요청_없음", async () => {
    setup(post());
    await userEvent.click(await screen.findByRole("button", { name: "확인" }));
    fireEvent.click(await screen.findByRole("dialog"));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
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
    await act("확인", "확인");

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
    await act("확인", "확인");

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
    await act("확인", "확인");

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
    setup(
      post({
        status: "FAILED",
        failureReason: "게시에 실패했습니다",
        retryable: true,
      }),
    );
    expect(await screen.findByText("게시에 실패했습니다")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(postCalls()).toHaveLength(1));
    expect(postCalls()[0][0]).toBe("/api/v1/posts/7/retry");
    expect(screen.getByRole("link", { name: "수정" })).toBeInTheDocument();
  });

  it("hides retry and asks to edit when the failure is not retryable", async () => {
    setup(
      post({
        status: "FAILED",
        failedStage: "PUBLISHING",
        failureReason: "캡션이 2,200자를 넘었습니다.",
        retryable: false,
      }),
    );
    expect(
      await screen.findByText("[수정]에서 고친 뒤 다시 게시해 주세요."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "수정" })).toBeInTheDocument();
  });

  it("clears an earlier error when another action starts", async () => {
    let release: (v: PostDetailResponse) => void = () => {};
    apiFetchMock.mockImplementation(async (p: string, init?: RequestInit) => {
      if (p === "/api/v1/posts/7/cancel") {
        throw new ApiError("COMMON_INTERNAL_ERROR", "취소하지 못했습니다.");
      }
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
    await act("취소", "게시물 취소");
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "취소하지 못했습니다.",
    );

    await act("확인", "확인");

    await waitFor(() =>
      expect(screen.queryByRole("alert")).not.toBeInTheDocument(),
    );
    release(post({ status: "PUBLISHING" }));
  });

  it("clears the confirm error after the post fails and retry succeeds", async () => {
    let current = post();
    apiFetchMock.mockImplementation(async (p: string) => {
      if (p === "/api/v1/posts/7/confirm") {
        current = post({
          status: "FAILED",
          failedStage: "PUBLISHING",
          failureReason: "게시에 실패했습니다",
          retryable: true,
        });
        throw new ApiError(
          "POST_VERSION_CONFLICT",
          "이미 상태가 바뀌었습니다.",
        );
      }
      if (p === "/api/v1/posts/7/retry") {
        current = post({ status: "PUBLISHING" });
        return current;
      }
      return current;
    });
    render(
      <QueryProvider>
        <PostOverview id="7" />
      </QueryProvider>,
    );
    await act("확인", "확인");
    await userEvent.click(
      await screen.findByRole("button", { name: "다시 시도" }),
    );

    expect(await screen.findByText("게시 중입니다")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the publish caption as is, not reassembled parts", async () => {
    setup(
      post({
        captionHead: "옛 머리",
        breweryBlock: "바뀐 양조장 블록",
        publishCaption: "게시될 캡션 전문",
      }),
    );
    expect(await screen.findByText("게시될 캡션 전문")).toBeInTheDocument();
    expect(screen.queryByText(/옛 머리/)).not.toBeInTheDocument();
    expect(screen.queryByText(/바뀐 양조장 블록/)).not.toBeInTheDocument();
  });

  it("shows brewery phone, homepage and instagram when present", async () => {
    setup(
      post({
        brewery: {
          ...brewery,
          phone: "02-123-4567",
          homepage: "https://ksd.example",
          instagramHandle: "ksd_official",
        },
      }),
    );
    expect(await screen.findByText(/02-123-4567/)).toBeInTheDocument();
    expect(screen.getByText(/https:\/\/ksd\.example/)).toBeInTheDocument();
    expect(screen.getByText(/@ksd_official/)).toBeInTheDocument();
  });

  it("omits brewery contact fields that are missing", async () => {
    setup(post());
    await screen.findByText("막걸리");
    expect(screen.queryByText(/@/)).not.toBeInTheDocument();
  });

  it("shows a completion message when published", async () => {
    setup(
      post({ status: "PUBLISHED", permalink: "https://instagram.com/p/abc" }),
    );
    expect(await screen.findByText("게시했습니다")).toBeInTheDocument();
  });

  it("shows the analysis failure stage", async () => {
    setup(
      post({
        status: "FAILED",
        failedStage: "ANALYZING",
        failureReason: "라벨을 읽지 못했습니다",
      }),
    );
    expect(await screen.findByText("분석 실패")).toBeInTheDocument();
  });

  it("shows the publish failure stage", async () => {
    setup(
      post({
        status: "FAILED",
        failedStage: "PUBLISHING",
        failureReason: "게시에 실패했습니다",
      }),
    );
    expect(await screen.findByText("게시 실패")).toBeInTheDocument();
  });

  it("shows a progress indicator while analyzing and publishing", async () => {
    setup(post({ status: "ANALYZING" }));
    const status = await screen.findByRole("status");
    expect(status.querySelector(".animate-pulse")).not.toBeNull();
  });

  it("shows a canceled message", async () => {
    setup(post({ status: "CANCELED" }));
    expect(await screen.findByText("취소한 게시물입니다")).toBeInTheDocument();
  });
});
