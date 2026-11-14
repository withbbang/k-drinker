// 로그인 후 이동 경로. 같은 사이트의 단일 `/` 경로만 허용하고 그 외는 `/`.
export function safeNext(value: string | null | undefined): string {
  if (!value || !value.startsWith("/")) {
    return "/";
  }
  if (value.startsWith("//") || value.startsWith("/\\")) {
    return "/";
  }
  return value;
}
