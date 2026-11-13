import type { ApiResponse } from "@/types/api/common";

const UNREACHABLE_MESSAGE =
  "서버와 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";

export class ApiError extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
  }
}

export async function apiFetch<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, init);
  } catch (error) {
    // React Query 취소는 그대로 전달한다.
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw new ApiError("COMMON_INTERNAL_ERROR", UNREACHABLE_MESSAGE);
  }
  if (response.status === 204) {
    return undefined as T;
  }

  let body: ApiResponse<T>;
  try {
    body = (await response.json()) as ApiResponse<T>;
  } catch {
    // 프록시 오류 페이지(HTML) 등 JSON이 아닌 응답
    throw new ApiError("COMMON_INTERNAL_ERROR", UNREACHABLE_MESSAGE);
  }
  // Spring 기본 /error JSON 등 응답 래퍼가 아닌 경우
  if (typeof body?.success !== "boolean") {
    throw new ApiError("COMMON_INTERNAL_ERROR", UNREACHABLE_MESSAGE);
  }
  if (!body.success) {
    throw new ApiError(body.error.code, body.error.message);
  }
  return body.data;
}
