// 로그인 후 이동 경로. 같은 사이트의 단일 `/` 경로만 허용하고 그 외는 `/`.
export function safeNext(value: string | null | undefined): string {
  if (!value || !value.startsWith("/")) {
    return "/";
  }
  // 탭·개행 등 제어문자는 URL 파싱에서 제거되어 `//host`로 바뀐다. 역슬래시도 거부한다.
  if (/[\x00-\x20\\]/.test(value)) {
    return "/";
  }
  if (value.startsWith("//") || value.startsWith("/\\")) {
    return "/";
  }
  return value;
}
