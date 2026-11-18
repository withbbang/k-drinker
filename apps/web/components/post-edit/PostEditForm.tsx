"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import {
  BreweryPicker,
  type BreweryFields,
} from "@/components/post-edit/BreweryPicker";
import {
  DrinkPicker,
  type DrinkChoice,
} from "@/components/post-edit/DrinkPicker";
import { PhotoOrderList } from "@/components/post-edit/PhotoOrderList";
import { RatingInput } from "@/components/ui/RatingInput";
import { useBrewery } from "@/hooks/use-breweries";
import { usePost } from "@/hooks/use-post";
import { useUpdatePost } from "@/hooks/use-update-post";
import { ApiError } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";
import type { PostDetailResponse } from "@/types/api/post";

const MAX_MEMO_LENGTH = 2000;
const FALLBACK_ERROR = "저장에 실패했습니다. 잠시 후 다시 시도해 주세요.";
const TEXTAREA =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900";

function fieldsOf(brewery: BreweryResponse | null): BreweryFields {
  return {
    address: brewery?.address ?? "",
    phone: brewery?.phone ?? "",
    homepage: brewery?.homepage ?? "",
    instagramHandle: brewery?.instagramHandle ?? "",
    otherSns: brewery?.otherSns ?? "",
  };
}

const orNull = (value: string) => (value.trim() === "" ? null : value.trim());

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
  const base = picked ?? current;
  // 양조장 칸은 그 칸을 채운 양조장과 함께 둔다. 보이는 양조장이 바뀌면 그 양조장 값으로 다시 채운다.
  const [edits, setEdits] = useState({
    breweryId: post.brewery?.id ?? null,
    fields: fieldsOf(post.brewery),
  });
  const baseId = base?.id ?? null;
  const fields = edits.breweryId === baseId ? edits.fields : fieldsOf(base);
  const [rating, setRating] = useState(post.rating);
  const [memo, setMemo] = useState(post.memo ?? "");
  const [captionHead, setCaptionHead] = useState(post.captionHead ?? "");
  const [captionTail, setCaptionTail] = useState(post.captionTail ?? "");
  const [photos, setPhotos] = useState(post.photos);
  // 사진 애니메이션 중에는 변경이 아직 photos에 반영되지 않았으므로 저장을 막는다.
  const [photosBusy, setPhotosBusy] = useState(false);

  const hasDrink = drink.drinkId !== null || newName !== "";

  const handleDrinkChange = (next: DrinkChoice) => {
    if (next.drinkId !== drink.drinkId) setPicked(null);
    setDrink(next);
  };

  const handleFieldsChange = (next: BreweryFields) =>
    setEdits({ breweryId: baseId, fields: next });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (photosBusy) return;
    const original = fieldsOf(base);
    const changed = (Object.keys(fields) as (keyof BreweryFields)[]).some(
      (key) => orNull(fields[key]) !== orNull(original[key]),
    );
    // 저장하면 새 버전으로 Editor가 다시 만들어지므로 mutate 콜백 대신 Promise로 이동한다.
    onSave
      .mutateAsync({
        version: post.version,
        drinkId: newName === "" ? drink.drinkId : null,
        newDrinkName: newName === "" ? null : newName,
        breweryId: hasDrink ? (picked?.id ?? null) : null,
        breweryEdit:
          hasDrink && base && changed
            ? {
                name: base.name,
                address: orNull(fields.address),
                phone: orNull(fields.phone),
                homepage: orNull(fields.homepage),
                instagramHandle: orNull(fields.instagramHandle),
                otherSns: orNull(fields.otherSns),
              }
            : null,
        rating,
        memo,
        captionHead,
        captionTail,
        photos: photos.map(({ id, fitMode }) => ({ id, fitMode })),
      })
      .then(() => router.push(`/posts/${post.id}`))
      // 실패는 onSave.isError로 아래에 표시한다.
      .catch(() => undefined);
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <DrinkPicker value={drink} onChange={handleDrinkChange} />
      <BreweryPicker
        disabled={!hasDrink}
        brewery={base}
        fields={fields}
        onPick={setPicked}
        onFieldsChange={handleFieldsChange}
      />
      <RatingInput value={rating} onChange={setRating} />
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
      <PhotoOrderList
        photos={photos}
        version={post.version}
        onChange={setPhotos}
        onBusyChange={setPhotosBusy}
      />
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

  // 저장 뒤 새 버전이 오면 폼 상태를 서버 값으로 다시 채운다.
  return <Editor key={post.data.version} post={post.data} onSave={update} />;
}
