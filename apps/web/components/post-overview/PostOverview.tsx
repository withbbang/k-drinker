"use client";

import Link from "next/link";

import { usePost } from "@/hooks/use-post";
import { usePostAction } from "@/hooks/use-post-action";
import { ApiError } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

const FALLBACK_ERROR =
  "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const BUTTON =
  "rounded-md px-4 py-2.5 text-center font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40";
const PRIMARY = `${BUTTON} bg-neutral-900 text-white hover:bg-neutral-800`;
const SECONDARY = `${BUTTON} border border-neutral-300 bg-white text-neutral-900 hover:bg-neutral-100`;

function Review({ post }: { post: PostDetailResponse }) {
  const caption = [post.captionHead, post.breweryBlock, post.captionTail]
    .filter((part): part is string => !!part)
    .join("\n\n");
  return (
    <div className="flex flex-col gap-4">
      <ul className="flex flex-col gap-2">
        {post.photos.map((photo, i) => (
          <li key={photo.id}>
            {/* eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL */}
            <img
              src={`${photo.url}?v=${post.version}`}
              alt={`사진 ${i + 1}`}
              className="w-full rounded-md"
            />
          </li>
        ))}
      </ul>
      <p className="text-lg font-semibold">{post.drinkName ?? "이름 미확인"}</p>
      {post.brewery && (
        <p className="text-sm text-neutral-700">
          {post.brewery.name}
          {post.brewery.address ? ` · ${post.brewery.address}` : ""}
        </p>
      )}
      <p className="text-sm text-neutral-700">
        {post.rating !== null ? `★ ${post.rating.toFixed(1)}` : "별점 없음"}
      </p>
      <p className="whitespace-pre-wrap text-sm">{caption}</p>
    </div>
  );
}

function Overview({ post }: { post: PostDetailResponse }) {
  const confirm = usePostAction(String(post.id), "confirm");
  const cancel = usePostAction(String(post.id), "cancel");
  const retry = usePostAction(String(post.id), "retry");

  const busy = confirm.isPending || cancel.isPending || retry.isPending;
  const error = confirm.error ?? cancel.error ?? retry.error;
  const web = post.channel === "WEB";

  function run(
    action: typeof confirm,
    message: string | null,
    version: number,
  ) {
    if (message && !window.confirm(message)) return;
    action.mutate(version);
  }

  const edit = post.editable && (
    <Link href={`/posts/${post.id}/edit`} className={SECONDARY}>
      수정
    </Link>
  );

  let content;
  switch (post.status) {
    case "RECEIVED":
    case "ANALYZING":
      content = <p role="status">분석 중입니다</p>;
      break;
    case "PUBLISHING":
      content = <p role="status">게시 중입니다</p>;
      break;
    case "PUBLISHED":
      content = (
        <>
          <Review post={post} />
          {post.permalink && (
            <a
              href={post.permalink}
              target="_blank"
              rel="noopener noreferrer"
              className={PRIMARY}
            >
              인스타그램에서 보기
            </a>
          )}
        </>
      );
      break;
    case "CANCELED":
      content = <p>취소한 게시물입니다</p>;
      break;
    case "FAILED":
      content = (
        <>
          <p className="text-sm text-red-700">{post.failureReason}</p>
          {web ? (
            <div className="flex flex-col gap-2">
              <button
                type="button"
                disabled={busy}
                onClick={() => run(retry, null, post.version)}
                className={PRIMARY}
              >
                다시 시도
              </button>
              {edit}
            </div>
          ) : (
            <>
              <p>텔레그램에서 진행 중인 게시물입니다.</p>
              {edit}
            </>
          )}
        </>
      );
      break;
    case "PENDING_REVIEW":
      content = (
        <>
          <Review post={post} />
          {web ? (
            <div className="flex flex-col gap-2">
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  run(confirm, "인스타그램에 게시할까요?", post.version)
                }
                className={PRIMARY}
              >
                확인
              </button>
              {edit}
              <button
                type="button"
                disabled={busy}
                onClick={() =>
                  run(cancel, "이 게시물을 취소할까요?", post.version)
                }
                className={SECONDARY}
              >
                취소
              </button>
            </div>
          ) : (
            <>
              <p>텔레그램에서 진행 중인 게시물입니다.</p>
              {edit}
            </>
          )}
        </>
      );
      break;
    default:
      content = <p>상태: {post.status}</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {content}
      {error && (
        <p role="alert" className="text-sm text-red-700">
          {error instanceof ApiError ? error.message : FALLBACK_ERROR}
        </p>
      )}
    </div>
  );
}

export function PostOverview({ id }: { id: string }) {
  const post = usePost(id, { poll: true });

  if (post.isError && !post.data) {
    return (
      <p role="alert" className="text-sm text-red-700">
        {post.error instanceof ApiError ? post.error.message : FALLBACK_ERROR}
      </p>
    );
  }
  if (!post.data) return null;
  return <Overview post={post.data} />;
}
