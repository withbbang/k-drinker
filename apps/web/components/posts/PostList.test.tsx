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

  it("links reviewable posts to the edit screen", async () => {
    setup([
      summary({ id: 7 }),
      summary({ id: 8, status: "FAILED", drinkName: "청주" }),
    ]);

    expect(await screen.findByRole("link", { name: /막걸리/ })).toHaveAttribute(
      "href",
      "/posts/7/edit",
    );
    expect(screen.getByRole("link", { name: /청주/ })).toHaveAttribute(
      "href",
      "/posts/8/edit",
    );
    expect(apiFetchMock).toHaveBeenCalledWith("/api/v1/posts?page=1&size=20");
  });

  it("links published posts to instagram", async () => {
    setup([
      summary({
        status: "PUBLISHED",
        permalink: "https://instagram.com/p/abc",
      }),
    ]);

    const link = await screen.findByRole("link", { name: /막걸리/ });
    expect(link).toHaveAttribute("href", "https://instagram.com/p/abc");
    expect(link).toHaveAttribute("target", "_blank");
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
});
