import Link from "next/link";

import { buttonClass } from "@/components/ui/button";

type PagerProps = {
  /** 0부터 시작하는 현재 페이지 */
  page: number;
  totalPages: number;
  hrefFor: (page: number) => string;
};

const BASE = buttonClass("secondary");

export function Pager({ page, totalPages, hrefFor }: PagerProps) {
  const hasPrev = page > 0;
  const hasNext = page + 1 < totalPages;

  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-between">
      {hasPrev ? (
        <Link href={hrefFor(page - 1)} className={BASE}>
          이전
        </Link>
      ) : (
        <span aria-disabled="true" className={`${BASE} opacity-40`}>
          이전
        </span>
      )}
      <span className="text-sm tabular-nums text-muted">
        {page + 1} / {Math.max(totalPages, 1)}
      </span>
      {hasNext ? (
        <Link href={hrefFor(page + 1)} className={BASE}>
          다음
        </Link>
      ) : (
        <span aria-disabled="true" className={`${BASE} opacity-40`}>
          다음
        </span>
      )}
    </nav>
  );
}
