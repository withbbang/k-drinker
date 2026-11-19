"use client";

import { useRouter } from "next/navigation";
import { useId, useRef, useState, type FormEvent, type ReactNode } from "react";

import { BreweryPicker } from "@/components/post-edit/BreweryPicker";
import { CaptionEditor } from "@/components/post-edit/CaptionEditor";
import {
  DrinkPicker,
  type DrinkChoice,
} from "@/components/post-edit/DrinkPicker";
import {
  fromSaved,
  newItems,
  toUpdatePhotos,
  type PhotoItem,
} from "@/components/post-edit/photo-items";
import { PhotoViewerSheet } from "@/components/post-edit/PhotoViewerSheet";
import { overflowMessage } from "@/components/ui/AddPhotoTile";
import { AutoTextarea } from "@/components/ui/AutoTextarea";
import { BlobImage } from "@/components/ui/BlobImage";
import { buttonClass } from "@/components/ui/button";
import { PhotoStrip } from "@/components/ui/PhotoStrip";
import { RatingInput } from "@/components/ui/RatingInput";
import { Toast } from "@/components/ui/Toast";
import { TopBar } from "@/components/ui/TopBar";
import { UndoRedoBar } from "@/components/ui/UndoRedoBar";
import { useBrewery } from "@/hooks/use-breweries";
import { useCaptionPreview } from "@/hooks/use-caption-preview";
import { usePost } from "@/hooks/use-post";
import { useToast } from "@/hooks/use-toast";
import { useUndoable } from "@/hooks/use-undoable";
import { useUpdatePost } from "@/hooks/use-update-post";
import { ApiError } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PostDetailResponse } from "@/types/api/post";

const MAX_MEMO_LENGTH = 2000;
const MAX_PHOTOS = 10;
const FALLBACK_ERROR = "저장에 실패했습니다. 잠시 후 다시 시도해 주세요.";
const TITLE = "수정";

// 되돌리기 기록에 남기는 값. 양조장 시트에서 고친 양조장 정보는 서버에 바로 저장하므로 넣지 않는다.
type EditState = {
  photos: PhotoItem[];
  rating: number | null;
  drink: DrinkChoice;
  // 고른 양조장. 없으면 술의 현재 양조장을 그대로 둔다.
  picked: BreweryResponse | null;
  memo: string;
  captionHead: string;
  captionTail: string;
};

// 글자 칸의 되돌리기 묶음 키
type TextField = "memo" | "captionHead" | "captionTail" | "newDrinkName";

type EditorProps = {
  post: PostDetailResponse;
  onSave: ReturnType<typeof useUpdatePost>;
};

function photoImage(
  photo: PhotoItem,
  alt: string,
  version: number,
  className: string,
) {
  return photo.kind === "saved" ? (
    // eslint-disable-next-line @next/next/no-img-element -- 원본 미디어 URL
    <img src={`${photo.url}?v=${version}`} alt={alt} className={className} />
  ) : (
    <BlobImage file={photo.file} alt={alt} className={className} />
  );
}

function Editor({ post, onSave }: EditorProps) {
  const router = useRouter();
  const formId = useId();
  const { state, set, setTyping, endTyping, undo, redo, canUndo, canRedo } =
    useUndoable<EditState>({
      photos: fromSaved(post.photos),
      rating: post.rating,
      drink: {
        drinkId: post.drinkId,
        drinkName: post.drinkName,
        breweryId: post.brewery?.id ?? null,
        newDrinkName: "",
      },
      picked: null,
      memo: post.memo ?? "",
      captionHead: post.captionHead ?? "",
      captionTail: post.captionTail ?? "",
    });
  const { photos, rating, drink, picked, memo, captionHead, captionTail } =
    state;
  const { toast, show, dismiss } = useToast();
  const newName = drink.newDrinkName.trim();
  // 다른 술을 고르면 그 술의 양조장을 현재 양조장으로 보여 준다.
  // 새 이름이면 API가 게시물의 현재 양조장을 새 술에 붙이므로 그 양조장을 그대로 보여 준다.
  const drinkChanged = newName === "" && drink.drinkId !== post.drinkId;
  const drinkBrewery = useBrewery(drinkChanged ? drink.breweryId : null);
  const current = drinkChanged ? (drinkBrewery.data ?? null) : post.brewery;
  // 고른 양조장은 시트에서 고친 값이 보이도록 최신 조회 결과를 쓴다.
  const pickedFresh = useBrewery(picked?.id ?? null);
  const base = (picked && (pickedFresh.data ?? picked)) ?? current;
  // 새 사진 키 순번. 되돌려도 다시 쓰지 않아 키가 겹치지 않는다.
  const seq = useRef(0);
  const [notice, setNotice] = useState<string | null>(null);
  // 사진을 끄는 동안은 순서가 아직 photos에 반영되지 않았으므로 저장·되돌리기를 막는다.
  const [photosBusy, setPhotosBusy] = useState(false);
  // 크게 보기 시트를 연 사진
  const [viewingKey, setViewingKey] = useState<string | null>(null);
  const locked = photosBusy || onSave.isPending;

  // 어떤 변경이든 지난 알림과 "사진을 뺐어요" 토스트는 닫는다.
  const clearMessages = () => {
    setNotice(null);
    dismiss();
  };
  const change = (next: (prev: EditState) => EditState) => {
    clearMessages();
    set(next);
  };
  const typeText = (key: TextField, next: (prev: EditState) => EditState) => {
    clearMessages();
    setTyping(key, next);
  };
  const handleUndo = () => {
    clearMessages();
    undo();
  };
  const handleRedo = () => {
    clearMessages();
    redo();
  };

  const addPhotos = (files: File[]) => {
    const start = seq.current;
    seq.current += files.length;
    change((prev) => ({
      ...prev,
      photos: [...prev.photos, ...newItems(files, start)],
    }));
  };

  const removePhoto = (key: string) => {
    change((prev) => ({
      ...prev,
      photos: prev.photos.filter((p) => p.key !== key),
    }));
    show("사진을 뺐어요", { label: "실행 취소", onAction: handleUndo });
  };

  const togglePad = (key: string) =>
    change((prev) => ({
      ...prev,
      photos: prev.photos.map((p) =>
        p.key === key
          ? { ...p, fitMode: p.fitMode === "PAD" ? "CROP" : "PAD" }
          : p,
      ),
    }));

  const handleDrinkChange = (next: DrinkChoice) => {
    // 새 이름 칸에 글자만 친 경우는 글자 입력으로 묶는다.
    const typingName =
      next.drinkId === drink.drinkId &&
      next.drinkName === drink.drinkName &&
      next.breweryId === drink.breweryId;
    const apply = (prev: EditState): EditState => ({
      ...prev,
      drink: next,
      // 다른 술을 고르면 고른 양조장을 비워 그 술의 양조장을 쓴다.
      picked: next.drinkId !== prev.drink.drinkId ? null : prev.picked,
    });
    if (typingName) typeText("newDrinkName", apply);
    else change(apply);
  };

  const hasDrink = drink.drinkId !== null || newName !== "";

  const preview = useCaptionPreview(String(post.id), {
    drinkId: newName === "" ? drink.drinkId : null,
    newDrinkName: newName === "" ? null : newName,
    breweryId: hasDrink ? (picked?.id ?? null) : null,
    rating,
    captionHead,
    captionTail,
  });
  const previewLength = preview.data?.length ?? post.captionLength;
  const drinkNameFixed =
    !post.captionTemplated &&
    (newName !== "" || drink.drinkId !== post.drinkId);
  const drinkTitle =
    newName !== "" ? newName : drink.drinkId !== null ? drink.drinkName : null;

  const viewingIndex = photos.findIndex((p) => p.key === viewingKey);
  const viewing = viewingIndex === -1 ? null : photos[viewingIndex];

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (locked) return;
    const { photos: photoEdits, newPhotos } = toUpdatePhotos(photos);
    // 저장하면 새 버전으로 Editor가 다시 만들어지므로 mutate 콜백 대신 Promise로 이동한다.
    // 실패하면 사진·기록을 그대로 두어 다시 저장하거나 되돌릴 수 있다.
    onSave
      .mutateAsync({
        request: {
          version: post.version,
          drinkId: newName === "" ? drink.drinkId : null,
          newDrinkName: newName === "" ? null : newName,
          breweryId: hasDrink ? (picked?.id ?? null) : null,
          rating,
          memo,
          captionHead,
          captionTail,
          photos: photoEdits,
        },
        newPhotos,
      })
      .then(() => router.push(`/posts/${post.id}`))
      // 실패는 onSave.isError로 아래에 표시한다.
      .catch(() => undefined);
  };

  return (
    <>
      <TopBar
        title={TITLE}
        backHref={`/posts/${post.id}`}
        actions={
          <>
            <UndoRedoBar
              canUndo={canUndo}
              canRedo={canRedo}
              disabled={locked}
              onUndo={handleUndo}
              onRedo={handleRedo}
            />
            <button
              type="submit"
              form={formId}
              disabled={locked}
              className={buttonClass("primary", "ml-1 px-4")}
            >
              저장
            </button>
          </>
        }
      />
      <form
        id={formId}
        onSubmit={handleSubmit}
        className="flex flex-col gap-6 px-4 pb-24 pt-5"
      >
        <h2 className="font-serif text-2xl leading-snug text-text">
          {drinkTitle ?? "이름 없는 술"}
        </h2>
        <PhotoStrip
          items={photos}
          getKey={(photo) => photo.key}
          renderImage={(photo, className) =>
            photoImage(
              photo,
              `사진 ${photos.indexOf(photo) + 1}`,
              post.version,
              className,
            )
          }
          isPadded={(photo) => photo.fitMode === "PAD"}
          // 저장 중 바꾼 사진은 보낸 요청에 들어가지 않으므로 잠근다.
          disabled={onSave.isPending}
          max={MAX_PHOTOS}
          onReorder={(next) => change((prev) => ({ ...prev, photos: next }))}
          onRemove={removePhoto}
          onOpen={setViewingKey}
          onBusyChange={setPhotosBusy}
          onAdd={addPhotos}
          onOverflow={(skipped) => setNotice(overflowMessage(skipped))}
        />
        <DrinkPicker
          value={drink}
          onChange={handleDrinkChange}
          onNewNameBlur={endTyping}
        />
        <BreweryPicker
          disabled={!hasDrink}
          brewery={base}
          onPick={(next) => change((prev) => ({ ...prev, picked: next }))}
        />
        <RatingInput
          value={rating}
          onChange={(value) => change((prev) => ({ ...prev, rating: value }))}
          disabled={onSave.isPending}
        />
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-text">메모</span>
          <AutoTextarea
            value={memo}
            maxLength={MAX_MEMO_LENGTH}
            onChange={(event) => {
              const value = event.target.value;
              typeText("memo", (prev) => ({ ...prev, memo: value }));
            }}
            onBlur={endTyping}
          />
        </label>
        <CaptionEditor
          head={captionHead}
          tail={captionTail}
          onHeadChange={(value) =>
            typeText("captionHead", (prev) => ({ ...prev, captionHead: value }))
          }
          onTailChange={(value) =>
            typeText("captionTail", (prev) => ({ ...prev, captionTail: value }))
          }
          onFieldBlur={endTyping}
          templated={post.captionTemplated}
          preview={{
            caption: preview.data?.caption ?? post.publishCaption,
            length: previewLength,
            fetching: preview.isFetching,
            error: preview.isError,
          }}
        />
        {drinkNameFixed && (
          <p className="text-sm text-muted">
            이 글은 이름표가 없어 술 이름이 자동으로 바뀌지 않아요
          </p>
        )}
        {notice !== null && (
          <p role="status" className="text-sm text-muted">
            {notice}
          </p>
        )}
        {onSave.isError && (
          <p role="alert" className="text-sm text-danger">
            {onSave.error instanceof ApiError
              ? onSave.error.message
              : FALLBACK_ERROR}
          </p>
        )}
      </form>
      {viewing && (
        <PhotoViewerSheet
          title={`${viewingIndex + 1}번째 사진`}
          image={photoImage(
            viewing,
            `${viewingIndex + 1}번째 사진 크게 보기`,
            post.version,
            `aspect-[4/5] w-full max-w-xs rounded-thumb border border-line ${
              viewing.fitMode === "PAD"
                ? "object-contain bg-bg"
                : "object-cover"
            }`,
          )}
          padded={viewing.fitMode === "PAD"}
          onTogglePad={() => togglePad(viewing.key)}
          onClose={() => setViewingKey(null)}
          disabled={onSave.isPending}
        />
      )}
      <Toast toast={toast} onDismiss={dismiss} />
    </>
  );
}

// 편집기를 띄우기 전 상태(오류·읽기 전용)에도 같은 상단 바를 둔다.
function Shell({ id, children }: { id: string; children: ReactNode }) {
  return (
    <>
      <TopBar title={TITLE} backHref={`/posts/${id}`} />
      <div className="flex flex-col gap-2 px-4 pt-5 text-text">{children}</div>
    </>
  );
}

export function PostEditForm({ id }: { id: string }) {
  const post = usePost(id);
  const update = useUpdatePost(id);

  if (post.isError) {
    return (
      <Shell id={id}>
        <p role="alert" className="text-sm text-danger">
          {post.error instanceof ApiError ? post.error.message : FALLBACK_ERROR}
        </p>
      </Shell>
    );
  }
  if (!post.data) return <Shell id={id}>{null}</Shell>;

  if (!post.data.editable) {
    return (
      <Shell id={id}>
        <p>상태: {post.data.status}</p>
        <p>지금은 수정할 수 없는 게시물입니다.</p>
      </Shell>
    );
  }

  // 게시물 id로만 키를 둔다. 양조장 시트 저장 등으로 다시 불러와 version이 바뀌어도 입력이 남는다.
  // 저장 요청의 version과 양조장 블록은 Editor가 받은 최신 post에서 읽는다.
  return <Editor key={post.data.id} post={post.data} onSave={update} />;
}
