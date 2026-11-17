// POST /share-target(공유 시트)만 가로채 사진을 Cache에 담고 /share로 보낸다.
const SHARE_CACHE = "kd-share";

self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

self.addEventListener("fetch", (event) => {
  const { request } = event;
  if (request.method !== "POST" || new URL(request.url).pathname !== "/share-target") {
    return;
  }
  event.respondWith(handleShare(request));
});

async function handleShare(request) {
  const shareUrl = new URL("/share", request.url);
  try {
    await storePhotos(request);
  } catch {
    // 저장에 실패해도 공유 화면으로 보낸다. 남은 조각은 비워 "공유된 사진이 없습니다"로 보이게 한다.
    try {
      await caches.delete(SHARE_CACHE);
    } catch {
      // 정리도 실패하면 어쩔 수 없다.
    }
  }
  return Response.redirect(shareUrl.href, 303);
}

async function storePhotos(request) {
  const formData = await request.formData();
  const photos = formData.getAll("photos").filter((item) => typeof item !== "string");

  await caches.delete(SHARE_CACHE);
  const cache = await caches.open(SHARE_CACHE);
  await Promise.all(
    photos.map((photo, i) =>
      cache.put(
        `/shared/${i}`,
        new Response(photo, { headers: { "Content-Type": photo.type } }),
      ),
    ),
  );
  await cache.put(
    "/shared/meta",
    new Response(JSON.stringify({ count: photos.length }), {
      headers: { "Content-Type": "application/json" },
    }),
  );
}
