"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";

import { Pager } from "@/components/ui/Pager";
import { SkeletonList } from "@/components/ui/Skeleton";
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

type Tone = "action" | "danger" | "muted";

const STATUS_TONE: Record<string, Tone> = {
  PENDING_REVIEW: "action",
  FAILED: "danger",
};

const TONE_CLASS: Record<Tone, string> = {
  action: "rounded-full bg-accent px-2 text-xs font-semibold text-on-accent",
  danger: "text-xs font-semibold text-danger",
  muted: "text-xs text-muted",
};

const ROW =
  "flex items-center gap-3 rounded-lg border border-line bg-surface p-3 text-text active:bg-line";

function pageOf(value: string | null): number {
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : 0;
}

function PostRow({ post }: { post: PostSummaryResponse }) {
  const tone = STATUS_TONE[post.status] ?? "muted";
  const body = (
    <>
      {post.thumbnailUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={post.thumbnailUrl}
          alt=""
          className="aspect-[4/5] w-14 shrink-0 rounded-thumb object-cover"
        />
      ) : (
        <span className="aspect-[4/5] w-14 shrink-0 rounded-thumb bg-line" />
      )}
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="truncate font-serif text-lg">
          {post.drinkName ?? "이름 미확인"}
        </span>
        <span className="text-sm text-muted">
          {post.rating !== null ? (
            <>
              <span className="text-accent">★</span> {post.rating.toFixed(1)}
            </>
          ) : (
            "별점 없음"
          )}
        </span>
        <span className="flex items-center gap-2 text-xs text-muted">
          <span data-tone={tone} className={TONE_CLASS[tone]}>
            {STATUS_LABELS[post.status] ?? "기타"}
          </span>
          <span>{formatDate(post.createdAt)}</span>
        </span>
      </span>
    </>
  );

  return (
    <Link href={`/posts/${post.id}`} className={ROW}>
      {body}
    </Link>
  );
}

export function PostList() {
  const page = pageOf(useSearchParams().get("page"));
  const posts = usePosts(page);

  if (posts.isPending) {
    return (
      <SkeletonList
        count={4}
        itemClassName="h-[calc(3.5rem*5/4+1.5rem+2px)] w-full"
      />
    );
  }
  if (posts.isError) {
    return (
      <p role="alert" className="text-sm text-danger">
        {posts.error instanceof ApiError
          ? posts.error.message
          : "기록을 불러오지 못했어요."}
      </p>
    );
  }
  const { items, totalPages } = posts.data;
  return (
    <div className="flex flex-col gap-4">
      {items.length === 0 ? (
        <div className="flex flex-col gap-1 py-6">
          <p className="text-lg text-text">아직 기록이 없어요.</p>
          <p className="text-sm text-muted">
            휴대폰 사진 앱에서 k-drinker로 공유하거나 텔레그램 봇에 사진을
            보내면 여기에 쌓여요.
          </p>
        </div>
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
