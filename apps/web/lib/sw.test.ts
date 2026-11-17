import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type FetchEvent = {
  request: Request;
  respondWith: (response: Promise<Response>) => void;
};

const source = readFileSync(resolve(__dirname, "../public/sw.js"), "utf8");

function loadWorker() {
  const listeners: Record<string, (event: unknown) => void> = {};
  const fakeSelf = {
    skipWaiting: vi.fn(),
    clients: { claim: vi.fn() },
    addEventListener: (type: string, listener: (event: unknown) => void) => {
      listeners[type] = listener;
    },
  };
  new Function("self", source)(fakeSelf);
  return listeners;
}

async function share(request: Request): Promise<Response | undefined> {
  let responded: Promise<Response> | undefined;
  loadWorker().fetch({
    request,
    respondWith: (response: Promise<Response>) => {
      responded = response;
    },
  } satisfies FetchEvent);
  return responded;
}

function shareRequest() {
  const body = new FormData();
  body.append("photos", new File(["a"], "a.jpg", { type: "image/jpeg" }));
  return new Request("https://kd.example/share-target", {
    method: "POST",
    body,
  });
}

describe("sw.js share-target", () => {
  const cachesMock = {
    delete: vi.fn(),
    open: vi.fn(),
  };

  beforeEach(() => {
    cachesMock.delete.mockReset().mockResolvedValue(true);
    cachesMock.open.mockReset();
    vi.stubGlobal("caches", cachesMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("redirects to /share after caching photos", async () => {
    const put = vi.fn().mockResolvedValue(undefined);
    cachesMock.open.mockResolvedValue({ put });

    const response = await (await share(shareRequest()))!;

    expect(response.status).toBe(303);
    expect(response.headers.get("Location")).toBe("https://kd.example/share");
    expect(put).toHaveBeenCalledTimes(2);
  });

  it("still redirects to /share and clears the cache when storing fails", async () => {
    cachesMock.open.mockResolvedValue({
      put: vi.fn().mockRejectedValue(new Error("QuotaExceededError")),
    });

    const response = await (await share(shareRequest()))!;

    expect(response.status).toBe(303);
    expect(response.headers.get("Location")).toBe("https://kd.example/share");
    expect(cachesMock.delete).toHaveBeenCalledTimes(2);
  });

  it("still redirects when the form data cannot be parsed", async () => {
    const request = new Request("https://kd.example/share-target", {
      method: "POST",
      headers: { "Content-Type": "multipart/form-data; boundary=x" },
      body: "garbage",
    });

    const response = await (await share(request))!;

    expect(response.status).toBe(303);
  });

  it("does not intercept other requests", async () => {
    const result = await share(new Request("https://kd.example/api/v1/posts"));

    expect(result).toBeUndefined();
  });
});
