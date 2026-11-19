import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BreweryList } from "@/components/breweries/BreweryList";
import { QueryProvider } from "@/components/providers/QueryProvider";
import BreweriesPage from "@/app/breweries/page";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PageResponse } from "@/types/api/common";

const { apiFetchMock, searchState } = vi.hoisted(() => ({
  apiFetchMock: vi.fn(),
  searchState: { query: "" },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/breweries",
  useRouter: () => ({ replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(searchState.query),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

function brewery(overrides: Partial<BreweryResponse> = {}): BreweryResponse {
  return {
    id: 1,
    name: "해창주조장",
    address: "전남 해남군",
    phone: null,
    homepage: null,
    instagramHandle: null,
    otherSns: null,
    source: "MANUAL",
    verified: true,
    ...overrides,
  };
}

function setup(items: BreweryResponse[], ui = <BreweryList />) {
  const page: PageResponse<BreweryResponse> = {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: 1,
  };
  apiFetchMock.mockResolvedValue(page);
  render(<QueryProvider>{ui}</QueryProvider>);
}

describe("BreweryList", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    searchState.query = "";
  });

  it("추가_링크가_제목_줄에_있다", async () => {
    setup([], <BreweriesPage />);
    const link = await screen.findByRole("link", { name: "추가" });
    expect(link).toHaveAttribute("href", "/breweries/new");
  });

  it("자동으로_찾은_양조장만_표시한다", async () => {
    setup([
      brewery({ id: 1, name: "해창주조장", verified: false }),
      brewery({ id: 2, name: "느린마을", verified: true }),
    ]);
    expect(await screen.findAllByText("확인 필요")).toHaveLength(1);
    expect(screen.getByText("해창주조장")).toBeInTheDocument();
    expect(screen.queryByText("검증됨")).not.toBeInTheDocument();
    expect(screen.queryByText("미검증")).not.toBeInTheDocument();
  });

  it("검색_결과가_없으면_검색어와_추가_안내", async () => {
    searchState.query = "q=해창";
    setup([]);
    expect(
      await screen.findByText("‘해창’에 맞는 양조장이 없어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "새 양조장 추가" }),
    ).toHaveAttribute("href", "/breweries/new");
  });

  it("검색어_없이_비면_추가_안내", async () => {
    setup([]);
    expect(
      await screen.findByText("아직 양조장이 없어요."),
    ).toBeInTheDocument();
  });
});
