import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it, vi } from "vitest";

import { viewport } from "./layout";
import manifest from "./manifest";

vi.mock("./fonts", () => ({
  pretendard: { variable: "" },
  gowunBatang: { variable: "" },
}));

const TOKENS = {
  bg: "#1D1713",
  surface: "#29211B",
  line: "#3A2F27",
  text: "#EDE5D8",
  muted: "#A8998A",
  accent: "#D6B04C",
  "on-accent": "#1D1713",
  danger: "#F08A7E",
};

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5]
    .map((i) => parseInt(hex.slice(i, i + 2), 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe("옹기 테마", () => {
  const css = readFileSync(join(__dirname, "globals.css"), "utf8");

  it("@theme 색 토큰 8개가 계획과 같다", () => {
    for (const [name, value] of Object.entries(TOKENS)) {
      const match = css.match(
        new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`),
      );
      expect(match?.[1].toUpperCase(), name).toBe(value.toUpperCase());
    }
  });

  it.each([
    ["text", "bg"],
    ["text", "surface"],
    ["muted", "bg"],
    ["muted", "surface"],
    ["accent", "bg"],
    ["accent", "surface"],
    ["on-accent", "accent"],
    ["danger", "bg"],
    ["danger", "surface"],
  ] as const)("%s / %s 대비가 4.5 이상", (fg, bg) => {
    expect(contrast(TOKENS[fg], TOKENS[bg])).toBeGreaterThanOrEqual(4.5);
  });

  it("매니페스트와 viewport 테마 색이 바탕색이다", () => {
    const m = manifest();
    expect(m.background_color).toBe("#1D1713");
    expect(m.theme_color).toBe("#1D1713");
    expect(viewport.themeColor).toBe("#1D1713");
  });
});

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return /\.tsx?$/.test(entry.name) && !/\.test\.tsx?$/.test(entry.name)
      ? [path]
      : [];
  });
}

describe("옛 색 정리", () => {
  it("화면 코드에 옛 색 클래스가 없다", () => {
    const root = join(__dirname, "..");
    const legacy =
      /\b(?:bg|text|border|outline|ring|divide)-(?:neutral|red|green|white|black)\b|-(?:neutral|red|green)-\d{2,3}\b/;
    const offenders = ["app", "components"]
      .flatMap((dir) => sourceFiles(join(root, dir)))
      .filter((file) => legacy.test(readFileSync(file, "utf8")))
      .map((file) => file.slice(root.length + 1));
    expect(
      offenders,
      `옛 색 클래스가 남은 파일: ${offenders.join(", ")}`,
    ).toEqual([]);
  });
});
