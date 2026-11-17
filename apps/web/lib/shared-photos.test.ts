import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  clearSharedPhotos,
  readSharedPhotos,
  SHARE_CACHE,
} from "@/lib/shared-photos";

function createFakeCaches() {
  const stores = new Map<string, Map<string, Response>>();
  return {
    async open(name: string) {
      if (!stores.has(name)) stores.set(name, new Map());
      const store = stores.get(name)!;
      return {
        async match(path: string) {
          return store.get(path)?.clone();
        },
        async put(path: string, response: Response) {
          store.set(path, response);
        },
        async keys() {
          return [...store.keys()].map(
            (path) => new Request(`http://x${path}`),
          );
        },
        async delete(request: Request | string) {
          const path =
            typeof request === "string"
              ? request
              : new URL(request.url).pathname;
          return store.delete(path);
        },
      };
    },
    async delete(name: string) {
      return stores.delete(name);
    },
  };
}

async function seed(files: { type: string; body: string }[]) {
  const cache = await caches.open(SHARE_CACHE);
  for (const [i, file] of files.entries()) {
    await cache.put(
      `/shared/${i}`,
      new Response(file.body, { headers: { "Content-Type": file.type } }),
    );
  }
  await cache.put(
    "/shared/meta",
    new Response(JSON.stringify({ count: files.length })),
  );
}

describe("shared-photos", () => {
  beforeEach(() => {
    vi.stubGlobal("caches", createFakeCaches());
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns photos in shared order", async () => {
    await seed([
      { type: "image/jpeg", body: "a" },
      { type: "image/png", body: "b" },
    ]);

    const photos = await readSharedPhotos();

    expect(photos.map((p) => [p.name, p.type])).toEqual([
      ["shared-0.jpg", "image/jpeg"],
      ["shared-1.png", "image/png"],
    ]);
    expect(await photos[1].text()).toBe("b");
  });

  it("returns empty when nothing was shared", async () => {
    expect(await readSharedPhotos()).toEqual([]);
  });

  it("returns empty when Cache API is missing", async () => {
    vi.stubGlobal("caches", undefined);

    expect(await readSharedPhotos()).toEqual([]);
  });

  it("clears shared photos", async () => {
    await seed([{ type: "image/jpeg", body: "a" }]);

    await clearSharedPhotos();

    expect(await readSharedPhotos()).toEqual([]);
  });
});
