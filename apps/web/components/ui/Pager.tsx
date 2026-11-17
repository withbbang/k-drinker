import Link from "next/link";

type PagerProps = {
  /** 0부터 시작하는 현재 페이지 */
  page: number;
  totalPages: number;
  hrefFor: (page: number) => string;
};

const BASE = "rounded-full border border-neutral-300 px-4 py-2 text-sm";

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
      <span className="text-sm text-neutral-700">
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
