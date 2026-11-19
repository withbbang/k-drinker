"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Pager } from "@/components/ui/Pager";
import { SkeletonList } from "@/components/ui/Skeleton";
import { buttonClass } from "@/components/ui/button";
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
          placeholder="양조장 이름"
          aria-label="양조장 검색"
          className="min-w-0 flex-1 rounded-field border border-line bg-surface px-3 py-2.5 text-text placeholder:text-muted"
        />
        <button type="submit" className={buttonClass("secondary")}>
          검색
        </button>
      </form>
      {breweries.isPending && (
        <SkeletonList count={4} itemClassName="h-16 w-full" />
      )}
      {breweries.isError && (
        <p role="alert" className="text-sm text-danger">
          {breweries.error instanceof ApiError
            ? breweries.error.message
            : "양조장을 불러오지 못했어요."}
        </p>
      )}
      {breweries.data && (
        <>
          {breweries.data.items.length === 0 ? (
            <div className="flex flex-col items-start gap-1 py-6">
              <p className="text-lg text-text">
                {q ? `‘${q}’에 맞는 양조장이 없어요.` : "아직 양조장이 없어요."}
              </p>
              <Link
                href="/breweries/new"
                className={buttonClass("text", "-ml-3")}
              >
                새 양조장 추가
              </Link>
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {breweries.data.items.map((brewery) => (
                <li key={brewery.id}>
                  <Link
                    href={`/breweries/${brewery.id}`}
                    className="flex items-center gap-3 rounded-lg border border-line bg-surface p-3 text-text active:bg-line"
                  >
                    <span className="flex min-w-0 flex-1 flex-col gap-1">
                      <span className="font-medium text-text">
                        {brewery.name}
                      </span>
                      {brewery.address && (
                        <span className="text-sm text-muted">
                          {brewery.address}
                        </span>
                      )}
                    </span>
                    {!brewery.verified && (
                      <span className="shrink-0 text-xs text-accent">
                        확인 필요
                      </span>
                    )}
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
