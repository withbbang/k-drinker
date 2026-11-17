const SEOUL = { timeZone: "Asia/Seoul" } as const;

const DATE = new Intl.DateTimeFormat("ko-KR", {
  ...SEOUL,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const TIME = new Intl.DateTimeFormat("ko-KR", {
  ...SEOUL,
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

function part(parts: Intl.DateTimeFormatPart[], type: string): string {
  return parts.find((p) => p.type === type)?.value ?? "";
}

/** Asia/Seoul 기준 yyyy.MM.dd */
export function formatDate(iso: string): string {
  const p = DATE.formatToParts(new Date(iso));
  return `${part(p, "year")}.${part(p, "month")}.${part(p, "day")}`;
}

/** Asia/Seoul 기준 yyyy.MM.dd HH:mm */
export function formatDateTime(iso: string): string {
  const p = TIME.formatToParts(new Date(iso));
  return `${formatDate(iso)} ${part(p, "hour")}:${part(p, "minute")}`;
}
