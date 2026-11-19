export function Skeleton({ className }: { className: string }) {
  return (
    <div
      aria-hidden="true"
      className={`motion-safe:animate-pulse rounded-thumb bg-surface ${className}`}
    />
  );
}

// 불러오는 중 자리. 화면 읽기 프로그램에는 "불러오는 중"으로 한 번만 읽힌다.
export function SkeletonList({
  count,
  itemClassName,
  className = "flex flex-col gap-2",
}: {
  count: number;
  itemClassName: string;
  className?: string;
}) {
  return (
    <div role="status" aria-label="불러오는 중" className={className}>
      {Array.from({ length: count }, (_, i) => (
        <Skeleton key={i} className={itemClassName} />
      ))}
    </div>
  );
}
