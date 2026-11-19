import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PostList } from "@/components/posts/PostList";
import { QueryProvider } from "@/components/providers/QueryProvider";
import type { PageResponse } from "@/types/api/common";
import type { PostSummaryResponse } from "@/types/api/post";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams("page=1"),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

function summary(
  overrides: Partial<PostSummaryResponse> = {},
): PostSummaryResponse {
  return {
    id: 1,
    status: "PENDING_REVIEW",
    drinkName: "막걸리",
    rating: 4.5,
    thumbnailUrl: "/m/1.jpg",
    permalink: null,
    channel: "WEB",
    createdAt: "2026-11-17T16:00:00Z",
    ...overrides,
  };
}

function setup(items: PostSummaryResponse[]) {
  const page: PageResponse<PostSummaryResponse> = {
    items,
    page: 1,
    size: 20,
    totalElements: 45,
    totalPages: 3,
  };
  apiFetchMock.mockResolvedValue(page);
  render(
    <QueryProvider>
      <PostList />
    </QueryProvider>,
  );
}

describe("PostList", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
  });

  it("links reviewable posts to the overview screen", async () => {
    setup([
      summary({ id: 7 }),
      summary({ id: 8, status: "FAILED", drinkName: "청주" }),
    ]);

    expect(await screen.findByRole("link", { name: /막걸리/ })).toHaveAttribute(
      "href",
      "/posts/7",
    );
    expect(screen.getByRole("link", { name: /청주/ })).toHaveAttribute(
      "href",
      "/posts/8",
    );
    expect(apiFetchMock).toHaveBeenCalledWith("/api/v1/posts?page=1&size=20");
  });

  it("links published posts to the overview screen too", async () => {
    setup([
      summary({
        status: "PUBLISHED",
        permalink: "https://instagram.com/p/abc",
      }),
    ]);

    const link = await screen.findByRole("link", { name: /막걸리/ });
    expect(link).toHaveAttribute("href", "/posts/1");
  });

  it("shows status labels in Korean and the Seoul date", async () => {
    setup([
      summary({ id: 1, status: "RECEIVED" }),
      summary({ id: 2, status: "ANALYZING" }),
      summary({ id: 3, status: "PUBLISHING" }),
      summary({ id: 4, status: "CANCELED" }),
    ]);

    expect(await screen.findByText("접수됨")).toBeInTheDocument();
    expect(screen.getByText("분석 중")).toBeInTheDocument();
    expect(screen.getByText("게시 중")).toBeInTheDocument();
    expect(screen.getByText("취소됨")).toBeInTheDocument();
    // 2026-11-17T16:00Z 는 서울 기준 11월 18일
    expect(screen.getAllByText("2026.11.18")).toHaveLength(4);
  });

  it("shows a placeholder for an unknown drink", async () => {
    setup([summary({ drinkName: null, status: "ANALYZING" })]);

    expect(await screen.findByText("이름 미확인")).toBeInTheDocument();
  });

  it("손볼_상태는_강조해서_보인다", async () => {
    setup([
      summary({ id: 1, status: "PENDING_REVIEW" }),
      summary({ id: 2, status: "FAILED" }),
      summary({ id: 3, status: "PUBLISHED" }),
    ]);

    expect(await screen.findByText("검토 대기")).toHaveAttribute(
      "data-tone",
      "action",
    );
    expect(screen.getByText("실패")).toHaveAttribute("data-tone", "danger");
    expect(screen.getByText("게시됨")).toHaveAttribute("data-tone", "muted");
  });

  it("알_수_없는_상태_코드는_기타로_보인다", async () => {
    setup([summary({ status: "WEIRD" })]);

    expect(await screen.findByText("기타")).toBeInTheDocument();
    expect(screen.queryByText("WEIRD")).not.toBeInTheDocument();
  });

  it("술_이름은_고운바탕이다", async () => {
    setup([summary()]);

    expect(await screen.findByText("막걸리")).toHaveClass("font-serif");
  });

  it("기록이_없으면_올리는_방법을_안내한다", async () => {
    setup([]);

    expect(await screen.findByText("아직 기록이 없어요.")).toBeInTheDocument();
    expect(
      screen.getByText(
        "휴대폰 사진 앱에서 k-drinker로 공유하거나 텔레그램 봇에 사진을 보내면 여기에 쌓여요.",
      ),
    ).toBeInTheDocument();
  });

  it("불러오는_중에는_뼈대를_보인다", () => {
    apiFetchMock.mockReturnValue(new Promise(() => {}));
    render(
      <QueryProvider>
        <PostList />
      </QueryProvider>,
    );

    expect(
      screen.getByRole("status", { name: "불러오는 중" }),
    ).toBeInTheDocument();
  });
});
