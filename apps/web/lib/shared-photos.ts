export const SHARE_CACHE = "kd-share";

const META_PATH = "/shared/meta";

function extensionOf(contentType: string): string {
  return contentType === "image/png" ? "png" : "jpg";
}

// 서비스 워커(public/sw.js)가 Cache에 넣어 둔 공유 사진을 읽는다.
export async function readSharedPhotos(): Promise<File[]> {
  if (typeof caches === "undefined") return [];
  const cache = await caches.open(SHARE_CACHE);
  const metaResponse = await cache.match(META_PATH);
  if (!metaResponse) return [];

  const { count } = (await metaResponse.json()) as { count: number };
  const photos: File[] = [];
  for (let i = 0; i < count; i++) {
    const response = await cache.match(`/shared/${i}`);
    if (!response) continue;
    const type = response.headers.get("Content-Type") ?? "";
    const bytes = await response.arrayBuffer();
    photos.push(
      new File([bytes], `shared-${i}.${extensionOf(type)}`, { type }),
    );
  }
  return photos;
}

export async function clearSharedPhotos(): Promise<void> {
  if (typeof caches === "undefined") return;
  await caches.delete(SHARE_CACHE);
}
