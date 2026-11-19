export function Skeleton({ className }: { className: string }) {
  return (
    <div
      aria-hidden="true"
      className={`motion-safe:animate-pulse rounded-thumb bg-surface ${className}`}
    />
  );
}
