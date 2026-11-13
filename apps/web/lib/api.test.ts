import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiFetch } from "@/lib/api";

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiFetch", () => {
  it("returns data when success is true", async () => {
    // given
    stubFetch(jsonResponse({ success: true, data: { status: "UP" } }));

    // when
    const data = await apiFetch<{ status: string }>("/api/v1/health");

    // then
    expect(data).toEqual({ status: "UP" });
  });

  it("throws ApiError with code and message when success is false", async () => {
    // given
    stubFetch(
      jsonResponse(
        {
          success: false,
          error: {
            code: "COMMON_NOT_FOUND",
            message: "요청한 리소스를 찾을 수 없습니다.",
          },
        },
        404,
      ),
    );

    // when
    const result = apiFetch("/api/v1/nope");

    // then
    await expect(result).rejects.toMatchObject({
      name: "ApiError",
      code: "COMMON_NOT_FOUND",
      message: "요청한 리소스를 찾을 수 없습니다.",
    });
  });

  it("returns undefined for 204", async () => {
    // given
    const response = new Response(null, { status: 204 });
    const jsonSpy = vi.spyOn(response, "json");
    stubFetch(response);

    // when
    const data = await apiFetch<void>("/api/v1/items/1", { method: "DELETE" });

    // then
    expect(data).toBeUndefined();
    expect(jsonSpy).not.toHaveBeenCalled();
  });

  it("throws COMMON_INTERNAL_ERROR when the body is not JSON", async () => {
    // given
    stubFetch(new Response("<html>Bad Gateway</html>", { status: 502 }));

    // when
    const result = apiFetch("/api/v1/health");

    // then
    await expect(result).rejects.toBeInstanceOf(ApiError);
    await expect(result).rejects.toMatchObject({
      code: "COMMON_INTERNAL_ERROR",
      message: "서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    });
  });
});
