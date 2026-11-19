"use client";

import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";

import { BreweryPicker } from "@/components/post-edit/BreweryPicker";
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
import { PhotoOrderList } from "@/components/post-edit/PhotoOrderList";
import { overflowMessage } from "@/components/ui/AddPhotoTile";
import { RatingInput } from "@/components/ui/RatingInput";
import { UndoRedoBar } from "@/components/ui/UndoRedoBar";
import { useBrewery } from "@/hooks/use-breweries";
import { usePost } from "@/hooks/use-post";
import { useUndoable } from "@/hooks/use-undoable";
import { useUpdatePost } from "@/hooks/use-update-post";
import { ApiError } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PostDetailResponse } from "@/types/api/post";

const MAX_MEMO_LENGTH = 2000;
const FALLBACK_ERROR = "저장에 실패했습니다. 잠시 후 다시 시도해 주세요.";
const TEXTAREA =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900";

// 되돌리기 기록에 남기는 값. 글자 칸은 넣지 않는다.
type EditState = { photos: PhotoItem[]; rating: number | null };

type EditorProps = {
  post: PostDetailResponse;
  onSave: ReturnType<typeof useUpdatePost>;
};

function Editor({ post, onSave }: EditorProps) {
  const router = useRouter();
  const [drink, setDrink] = useState<DrinkChoice>({
    drinkId: post.drinkId,
    drinkName: post.drinkName,
    breweryId: post.brewery?.id ?? null,
    newDrinkName: "",
  });
  // 고른 양조장. 고르지 않으면 술의 현재 양조장을 그대로 둔다.
  const [picked, setPicked] = useState<BreweryResponse | null>(null);
  const newName = drink.newDrinkName.trim();
  // 다른 술을 고르면 그 술의 양조장을 현재 양조장으로 보여 준다.
  // 새 이름이면 API가 게시물의 현재 양조장을 새 술에 붙이므로 그 양조장을 그대로 보여 준다.
  const drinkChanged = newName === "" && drink.drinkId !== post.drinkId;
  const drinkBrewery = useBrewery(drinkChanged ? drink.breweryId : null);
  const current = drinkChanged ? (drinkBrewery.data ?? null) : post.brewery;
  // 고른 양조장은 시트에서 고친 값이 보이도록 최신 조회 결과를 쓴다.
  const pickedFresh = useBrewery(picked?.id ?? null);
  const base = (picked && (pickedFresh.data ?? picked)) ?? current;
  const [memo, setMemo] = useState(post.memo ?? "");
  const [captionHead, setCaptionHead] = useState(post.captionHead ?? "");
  const [captionTail, setCaptionTail] = useState(post.captionTail ?? "");
  const { state, set, undo, redo, canUndo, canRedo } = useUndoable<EditState>({
    photos: fromSaved(post.photos),
    rating: post.rating,
  });
  const { photos, rating } = state;
  // 새 사진 키 순번. 되돌려도 다시 쓰지 않아 키가 겹치지 않는다.
  const seq = useRef(0);
  const [notice, setNotice] = useState<string | null>(null);
  // 사진 애니메이션 중에는 변경이 아직 photos에 반영되지 않았으므로 저장을 막는다.
  const [photosBusy, setPhotosBusy] = useState(false);

  const change = (next: (prev: EditState) => EditState) => {
    setNotice(null);
    set(next);
  };

  const addPhotos = (files: File[]) => {
    const start = seq.current;
    seq.current += files.length;
    change((prev) => ({
      ...prev,
      photos: [...prev.photos, ...newItems(files, start)],
    }));
  };

  const hasDrink = drink.drinkId !== null || newName !== "";

  const handleDrinkChange = (next: DrinkChoice) => {
    if (next.drinkId !== drink.drinkId) setPicked(null);
    setDrink(next);
  };

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (photosBusy) return;
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
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <DrinkPicker value={drink} onChange={handleDrinkChange} />
      <BreweryPicker disabled={!hasDrink} brewery={base} onPick={setPicked} />
      <RatingInput
        value={rating}
        onChange={(value) => change((prev) => ({ ...prev, rating: value }))}
        disabled={onSave.isPending}
      />
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">메모</span>
        <input
          type="text"
          value={memo}
          maxLength={MAX_MEMO_LENGTH}
          onChange={(event) => setMemo(event.target.value)}
          className={TEXTAREA}
        />
      </label>
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">캡션 머리</span>
        <textarea
          rows={5}
          value={captionHead}
          onChange={(event) => setCaptionHead(event.target.value)}
          className={TEXTAREA}
        />
      </label>
      <div className="flex flex-col gap-2">
        <span className="text-sm font-medium">양조장 블록</span>
        <pre className="whitespace-pre-wrap rounded-md bg-neutral-100 p-3 text-sm">
          {post.breweryBlock ?? ""}
        </pre>
      </div>
      <label className="flex flex-col gap-2">
        <span className="text-sm font-medium">캡션 꼬리</span>
        <textarea
          rows={3}
          value={captionTail}
          onChange={(event) => setCaptionTail(event.target.value)}
          className={TEXTAREA}
        />
      </label>
      <p className="text-sm text-neutral-700">
        캡션 {post.captionLength.toLocaleString("ko-KR")}자 / 2,200자
      </p>
      <UndoRedoBar
        canUndo={canUndo}
        canRedo={canRedo}
        disabled={photosBusy || onSave.isPending}
        onUndo={() => {
          setNotice(null);
          undo();
        }}
        onRedo={() => {
          setNotice(null);
          redo();
        }}
      />
      <PhotoOrderList
        photos={photos}
        version={post.version}
        // 저장 중 바꾼 사진은 보낸 요청에 들어가지 않으므로 잠근다.
        disabled={onSave.isPending}
        onChange={(next) => change((prev) => ({ ...prev, photos: next }))}
        onBusyChange={setPhotosBusy}
        onAdd={addPhotos}
        onOverflow={(skipped) => setNotice(overflowMessage(skipped))}
      />
      {notice !== null && (
        <p role="status" className="text-sm text-neutral-700">
          {notice}
        </p>
      )}
      <button
        type="submit"
        disabled={onSave.isPending || photosBusy}
        className="rounded-md bg-neutral-900 px-4 py-2.5 font-semibold text-white hover:bg-neutral-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40"
      >
        저장
      </button>
      {onSave.isError && (
        <p role="alert" className="text-sm text-red-700">
          {onSave.error instanceof ApiError
            ? onSave.error.message
            : FALLBACK_ERROR}
        </p>
      )}
    </form>
  );
}

export function PostEditForm({ id }: { id: string }) {
  const post = usePost(id);
  const update = useUpdatePost(id);

  if (post.isError) {
    return (
      <p role="alert" className="text-sm text-red-700">
        {post.error instanceof ApiError ? post.error.message : FALLBACK_ERROR}
      </p>
    );
  }
  if (!post.data) return null;

  if (!post.data.editable) {
    return (
      <div className="flex flex-col gap-2">
        <p>상태: {post.data.status}</p>
        <p>지금은 수정할 수 없는 게시물입니다.</p>
      </div>
    );
  }

  // 게시물 id로만 키를 둔다. 양조장 시트 저장 등으로 다시 불러와 version이 바뀌어도 입력이 남는다.
  // 저장 요청의 version과 양조장 블록은 Editor가 받은 최신 post에서 읽는다.
  return <Editor key={post.data.id} post={post.data} onSave={update} />;
}
