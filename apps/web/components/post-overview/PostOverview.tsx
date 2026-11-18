"use client";

import { useEffect, useState } from "react";

import Link from "next/link";

import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
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

/** 실패 단계(`failedStage`, 실패 직전 상태) → 표시 문구. */
const FAILED_STAGE: Record<string, string> = {
  ANALYZING: "분석 실패",
  PUBLISHING: "게시 실패",
};

function Progress({ message }: { message: string }) {
  return (
    <p role="status" className="flex items-center gap-2">
      <span
        aria-hidden="true"
        className="inline-block size-2.5 animate-pulse rounded-full bg-neutral-500"
      />
      {message}
    </p>
  );
}

function BreweryInfo({
  brewery,
}: {
  brewery: NonNullable<PostDetailResponse["brewery"]>;
}) {
  const contacts = [
    brewery.phone,
    brewery.homepage,
    brewery.instagramHandle ? `@${brewery.instagramHandle}` : null,
  ].filter((v): v is string => !!v);
  return (
    <div className="text-sm text-neutral-700">
      <p>
        {brewery.name}
        {brewery.address ? ` · ${brewery.address}` : ""}
      </p>
      {contacts.map((contact) => (
        <p key={contact} className="break-all">
          {contact}
        </p>
      ))}
    </div>
  );
}

function Review({ post }: { post: PostDetailResponse }) {
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
      {post.brewery && <BreweryInfo brewery={post.brewery} />}
      <p className="text-sm text-neutral-700">
        {post.rating !== null ? `★ ${post.rating.toFixed(1)}` : "별점 없음"}
      </p>
      <p className="whitespace-pre-wrap text-sm">{post.publishCaption}</p>
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
  const resetConfirm = confirm.reset;
  const resetCancel = cancel.reset;
  const resetRetry = retry.reset;

  // 상태가 바뀌면 이전 동작의 오류는 더 이상 맞지 않으므로 지운다.
  useEffect(() => {
    resetConfirm();
    resetCancel();
    resetRetry();
  }, [post.status, resetConfirm, resetCancel, resetRetry]);

  // 확인이 필요한 동작은 모달에서 승인을 받은 뒤 실행한다.
  // 버튼을 누른 시점의 version을 함께 저장해, 모달이 열린 사이 글이 바뀌면 충돌로 처리되게 한다.
  const [clicked, setPendingAction] = useState<{
    kind: "confirm" | "cancel";
    version: number;
    status: string;
  } | null>(null);

  // 상태나 버전이 바뀌면 사용자가 보지 못한 내용을 승인하지 않도록 모달을 닫는다.
  const pendingAction =
    clicked &&
    clicked.version === post.version &&
    clicked.status === post.status
      ? clicked
      : null;

  const dialog =
    pendingAction?.kind === "confirm"
      ? {
          title: "인스타그램에 게시할까요?",
          confirmLabel: "확인",
          cancelLabel: "취소",
        }
      : pendingAction?.kind === "cancel"
        ? {
            title: "이 게시물을 취소할까요?",
            confirmLabel: "게시물 취소",
            cancelLabel: "돌아가기",
          }
        : null;

  function run(action: typeof confirm, version: number) {
    // 새 동작을 시작하면 다른 동작의 오류가 남아 새 오류를 가리지 않게 지운다.
    for (const other of [confirm, cancel, retry]) {
      if (other !== action) other.reset();
    }
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
      content = <Progress message="분석 중입니다" />;
      break;
    case "PUBLISHING":
      content = <Progress message="게시 중입니다" />;
      break;
    case "PUBLISHED":
      content = (
        <>
          <p className="font-semibold">게시했습니다</p>
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
          {post.failedStage && FAILED_STAGE[post.failedStage] && (
            <p className="font-semibold text-red-700">
              {FAILED_STAGE[post.failedStage]}
            </p>
          )}
          <p className="text-sm text-red-700">{post.failureReason}</p>
          {web ? (
            <div className="flex flex-col gap-2">
              {post.retryable ? (
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => run(retry, post.version)}
                  className={PRIMARY}
                >
                  다시 시도
                </button>
              ) : (
                <p className="text-sm">
                  {post.editable
                    ? "[수정]에서 고친 뒤 다시 게시해 주세요."
                    : "다시 시도할 수 없는 게시물입니다."}
                </p>
              )}
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
                  setPendingAction({
                    kind: "confirm",
                    version: post.version,
                    status: post.status,
                  })
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
                  setPendingAction({
                    kind: "cancel",
                    version: post.version,
                    status: post.status,
                  })
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
      {pendingAction && dialog && (
        <ConfirmDialog
          title={dialog.title}
          confirmLabel={dialog.confirmLabel}
          cancelLabel={dialog.cancelLabel}
          onConfirm={() => {
            setPendingAction(null);
            run(
              pendingAction.kind === "confirm" ? confirm : cancel,
              pendingAction.version,
            );
          }}
          onClose={() => setPendingAction(null)}
        />
      )}
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
