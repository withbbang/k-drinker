"use client";

import { useEffect, useState, type ReactNode } from "react";

import Link from "next/link";

import { PhotoCarousel } from "@/components/post-overview/PhotoCarousel";
import { buttonClass } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { Skeleton } from "@/components/ui/Skeleton";
import { TopBar } from "@/components/ui/TopBar";
import { usePost } from "@/hooks/use-post";
import { usePostAction } from "@/hooks/use-post-action";
import { ApiError } from "@/lib/api";
import type { PostDetailResponse } from "@/types/api/post";

const FALLBACK_ERROR =
  "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
const TITLE = "후기";
const BACK_HREF = "/";

/** 실패 단계(`failedStage`, 실패 직전 상태) → 표시 문구. */
const FAILED_STAGE: Record<string, string> = {
  ANALYZING: "분석 실패",
  PUBLISHING: "게시 실패",
};

function Progress({ message }: { message: string }) {
  return (
    <p role="status" className="flex items-center gap-2 text-text">
      <span
        aria-hidden="true"
        className="inline-block size-2.5 rounded-full bg-accent motion-safe:animate-pulse"
      />
      {message}
    </p>
  );
}

// 사진 아래 글과 상태 문구는 좌우 여백 안에 둔다. 사진만 화면 가장자리까지 간다.
function Section({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-3 px-4">{children}</div>;
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
    <div className="text-sm text-muted">
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
    <>
      <PhotoCarousel photos={post.photos} version={post.version} />
      <Section>
        <div className="flex flex-col gap-1">
          <h2 className="font-serif text-2xl text-text">
            {post.drinkName ?? "이름 미확인"}
          </h2>
          <p className="text-sm text-accent">
            {post.rating !== null ? `★ ${post.rating.toFixed(1)}` : "별점 없음"}
          </p>
        </div>
        {post.brewery && <BreweryInfo brewery={post.brewery} />}
        <p className="whitespace-pre-wrap text-sm text-text">
          {post.publishCaption}
        </p>
      </Section>
    </>
  );
}

// 주 동작 하나를 화면 아래에 고정한다.
function ActionBar({ children }: { children: ReactNode }) {
  return (
    <div className="sticky bottom-0 border-t border-line bg-bg px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))]">
      {children}
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
          confirmLabel: "게시하기",
          cancelLabel: "돌아가기",
          body: null,
        }
      : pendingAction?.kind === "cancel"
        ? {
            title: "이 게시물을 취소할까요?",
            confirmLabel: "게시물 취소",
            cancelLabel: "돌아가기",
            body: (
              <p className="text-sm text-muted">취소하면 되돌릴 수 없어요.</p>
            ),
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
    <Link href={`/posts/${post.id}/edit`} className={buttonClass("text")}>
      수정
    </Link>
  );
  const telegramNotice = (
    <Section>
      <p className="text-text">텔레그램에서 진행 중인 게시물입니다.</p>
    </Section>
  );

  let content: ReactNode;
  let action: ReactNode = null;
  switch (post.status) {
    case "RECEIVED":
    case "ANALYZING":
      content = (
        <Section>
          <Progress message="분석 중입니다" />
        </Section>
      );
      break;
    case "PUBLISHING":
      content = (
        <Section>
          <Progress message="게시 중입니다" />
        </Section>
      );
      break;
    case "PUBLISHED":
      content = (
        <>
          <Section>
            <p className="font-semibold text-text">게시했습니다</p>
          </Section>
          <Review post={post} />
        </>
      );
      if (post.permalink) {
        action = (
          <a
            href={post.permalink}
            target="_blank"
            rel="noopener noreferrer"
            className={buttonClass("primary", "w-full")}
          >
            인스타그램에서 보기
          </a>
        );
      }
      break;
    case "CANCELED":
      content = (
        <Section>
          <p className="text-text">취소한 게시물입니다</p>
        </Section>
      );
      break;
    case "FAILED":
      content = (
        <>
          <Section>
            {post.failedStage && FAILED_STAGE[post.failedStage] && (
              <p className="font-semibold text-danger">
                {FAILED_STAGE[post.failedStage]}
              </p>
            )}
            <p className="text-sm text-danger">{post.failureReason}</p>
            {web && !post.retryable && (
              <p className="text-sm text-muted">
                {post.editable
                  ? "[수정]에서 고친 뒤 다시 게시해 주세요."
                  : "다시 시도할 수 없는 게시물입니다."}
              </p>
            )}
          </Section>
          {!web && telegramNotice}
        </>
      );
      if (web && post.retryable) {
        action = (
          <button
            type="button"
            disabled={busy}
            onClick={() => run(retry, post.version)}
            className={buttonClass("primary", "w-full")}
          >
            다시 시도
          </button>
        );
      }
      break;
    case "PENDING_REVIEW":
      content = (
        <>
          <Review post={post} />
          {web ? (
            <Section>
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
                className={buttonClass("text", "self-start text-danger")}
              >
                게시물 취소
              </button>
            </Section>
          ) : (
            telegramNotice
          )}
        </>
      );
      if (web) {
        action = (
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
            className={buttonClass("primary", "w-full")}
          >
            게시하기
          </button>
        );
      }
      break;
    default:
      content = (
        <Section>
          <p className="text-text">상태를 알 수 없어요.</p>
        </Section>
      );
  }

  return (
    <>
      <TopBar
        title={post.drinkName ?? "이름 미확인"}
        backHref={BACK_HREF}
        actions={edit || undefined}
      />
      <div className="flex flex-1 flex-col gap-5 pt-4 pb-6">
        {content}
        {error && (
          <Section>
            <p role="alert" className="text-sm text-danger">
              {error instanceof ApiError ? error.message : FALLBACK_ERROR}
            </p>
          </Section>
        )}
      </div>
      {action && <ActionBar>{action}</ActionBar>}
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
        >
          {dialog.body}
        </ConfirmDialog>
      )}
    </>
  );
}

function Loading() {
  return (
    <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4">
      <Skeleton className="aspect-[4/5] w-full" />
      <div className="flex flex-col gap-2 px-4">
        <Skeleton className="h-7 w-1/2" />
        <Skeleton className="h-4 w-3/4" />
      </div>
    </div>
  );
}

export function PostOverview({ id }: { id: string }) {
  const post = usePost(id, { poll: true });

  if (post.isError && !post.data) {
    return (
      <>
        <TopBar title={TITLE} backHref={BACK_HREF} />
        <div className="px-4 pt-4">
          <p role="alert" className="text-sm text-danger">
            {post.error instanceof ApiError
              ? post.error.message
              : FALLBACK_ERROR}
          </p>
        </div>
      </>
    );
  }
  if (!post.data) {
    return (
      <>
        <TopBar title={TITLE} backHref={BACK_HREF} />
        <Loading />
      </>
    );
  }
  return <Overview post={post.data} />;
}
