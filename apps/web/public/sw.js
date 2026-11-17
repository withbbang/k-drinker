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
  return Response.redirect("/share", 303);
}
