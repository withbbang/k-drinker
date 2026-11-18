import type { FitMode, PhotoEdit, PostPhotoResponse } from "@/types/api/post";

// 수정 화면의 사진 한 장. 저장된 사진과 새로 더한 파일을 한 목록에 섞어 둔다.
export type PhotoItem = { key: string; fitMode: FitMode } & (
  { kind: "saved"; id: number; url: string } | { kind: "new"; file: File }
);

export function fromSaved(photos: PostPhotoResponse[]): PhotoItem[] {
  return photos.map(({ id, url, fitMode }) => ({
    key: `saved-${id}`,
    kind: "saved",
    id,
    url,
    fitMode,
  }));
}

// 새 사진 키는 호출하는 쪽의 순번으로 만든다(되돌려도 다시 쓰지 않는다).
export function newItems(files: File[], startSeq: number): PhotoItem[] {
  return files.map((file, i) => ({
    key: `new-${startSeq + i}`,
    kind: "new",
    file,
    fitMode: "CROP",
  }));
}

// 새 사진은 목록에 나온 순서대로 newIndex 0,1,2…를 받고 같은 순서로 파일을 보낸다.
export function toUpdatePhotos(items: PhotoItem[]): {
  photos: PhotoEdit[];
  newPhotos: File[];
} {
  const newPhotos: File[] = [];
  const photos = items.map((item): PhotoEdit => {
    if (item.kind === "saved") return { id: item.id, fitMode: item.fitMode };
    newPhotos.push(item.file);
    return { newIndex: newPhotos.length - 1, fitMode: item.fitMode };
  });
  return { photos, newPhotos };
}
