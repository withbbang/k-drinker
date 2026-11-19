"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Pager } from "@/components/ui/Pager";
import { useBreweries } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";

function pageOf(value: string | null): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

export function BreweryList() {
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const page = pageOf(params.get("page"));
  const breweries = useBreweries(q, page);

  const hrefFor = (p: number) =>
    `/breweries?q=${encodeURIComponent(q)}&page=${p}`;

  return (
    <div className="flex flex-col gap-4">
      <form action="/breweries" className="flex gap-2">
        <input
          type="search"
          name="q"
          defaultValue={q}
          aria-label="양조장 검색"
          className="min-w-0 flex-1 rounded-field border border-line bg-surface px-3 py-2.5 text-text placeholder:text-muted"
        />
        <button
          type="submit"
          className="rounded-md border border-neutral-300 px-4 py-2.5"
        >
          검색
        </button>
      </form>
      <Link
        href="/breweries/new"
        className="rounded-md bg-neutral-900 px-4 py-3 text-center text-white"
      >
        새 양조장
      </Link>
      {breweries.isPending && (
        <p className="text-sm text-muted">불러오는 중…</p>
      )}
      {breweries.isError && (
        <p role="alert" className="text-sm text-danger">
          {breweries.error instanceof ApiError
            ? breweries.error.message
            : "양조장을 불러오지 못했습니다."}
        </p>
      )}
      {breweries.data && (
        <>
          {breweries.data.items.length === 0 ? (
            <p className="text-sm text-muted">양조장이 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {breweries.data.items.map((brewery) => (
                <li key={brewery.id}>
                  <Link
                    href={`/breweries/${brewery.id}`}
                    className="flex flex-col gap-1 rounded-lg border border-line bg-surface p-3 text-text"
                  >
                    <span className="font-medium">{brewery.name}</span>
                    <span className="text-sm text-muted">
                      {brewery.address ?? "주소 없음"}
                    </span>
                    <span className="text-xs text-muted">
                      {brewery.verified ? "검증됨" : "미검증"}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <Pager
            page={page}
            totalPages={breweries.data.totalPages}
            hrefFor={hrefFor}
          />
        </>
      )}
    </div>
  );
}
