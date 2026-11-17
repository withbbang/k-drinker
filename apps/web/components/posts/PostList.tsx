"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Pager } from "@/components/ui/Pager";
import { usePosts } from "@/hooks/use-posts";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import type { PostSummaryResponse } from "@/types/api/post";

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: "접수됨",
  ANALYZING: "분석 중",
  PENDING_REVIEW: "검토 대기",
  PUBLISHING: "게시 중",
  PUBLISHED: "게시됨",
  FAILED: "실패",
  CANCELED: "취소됨",
};

const ROW = "flex items-center gap-3 rounded-lg bg-white p-3 shadow-sm";

function pageOf(value: string | null): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

function PostRow({ post }: { post: PostSummaryResponse }) {
  const body = (
    <>
      {post.thumbnailUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={post.thumbnailUrl}
          alt=""
          className="size-16 shrink-0 rounded-md object-cover"
        />
      ) : (
        <span className="size-16 shrink-0 rounded-md bg-neutral-200" />
      )}
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="truncate font-medium">
          {post.drinkName ?? "이름 미확인"}
        </span>
        <span className="text-sm text-neutral-700">
          {post.rating !== null ? `★ ${post.rating.toFixed(1)}` : "별점 없음"}
        </span>
        <span className="flex gap-2 text-xs text-neutral-700">
          <span>{STATUS_LABELS[post.status] ?? post.status}</span>
          <span>{formatDate(post.createdAt)}</span>
        </span>
      </span>
    </>
  );

  if (post.status === "PENDING_REVIEW" || post.status === "FAILED") {
    return (
      <Link href={`/posts/${post.id}/edit`} className={ROW}>
        {body}
      </Link>
    );
  }
  if (post.status === "PUBLISHED" && post.permalink) {
    return (
      <a
        href={post.permalink}
        target="_blank"
        rel="noopener noreferrer"
        className={ROW}
      >
        {body}
      </a>
    );
  }
  return <div className={ROW}>{body}</div>;
}

export function PostList() {
  const page = pageOf(useSearchParams().get("page"));
  const posts = usePosts(page);

  if (posts.isPending) {
    return <p className="text-sm text-neutral-700">불러오는 중…</p>;
  }
  if (posts.isError) {
    return (
      <p role="alert" className="text-sm text-red-700">
        {posts.error instanceof ApiError
          ? posts.error.message
          : "기록을 불러오지 못했습니다."}
      </p>
    );
  }
  const { items, totalPages } = posts.data;
  return (
    <div className="flex flex-col gap-4">
      {items.length === 0 ? (
        <p className="text-sm text-neutral-700">아직 기록이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {items.map((post) => (
            <li key={post.id}>
              <PostRow post={post} />
            </li>
          ))}
        </ul>
      )}
      <Pager
        page={page}
        totalPages={totalPages}
        hrefFor={(p) => `/?page=${p}`}
      />
    </div>
  );
}
