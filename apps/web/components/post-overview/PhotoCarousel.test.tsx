import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PhotoCarousel } from "@/components/post-overview/PhotoCarousel";
import type { PostPhotoResponse } from "@/types/api/post";

function photos(count: number): PostPhotoResponse[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    position: i,
    url: `/m/${i + 1}.jpg`,
    fitMode: "CROP",
  }));
}

describe("PhotoCarousel", () => {
  it("사진마다_4:5로_그리고_번호를_보인다", () => {
    render(<PhotoCarousel photos={photos(3)} version={4} />);
    const imgs = screen.getAllByRole("img");
    expect(imgs).toHaveLength(3);
    imgs.forEach((img, i) => {
      expect(img).toHaveAttribute("alt", `사진 ${i + 1} / 3`);
      expect(img).toHaveAttribute("src", `/m/${i + 1}.jpg?v=4`);
      expect(img.parentElement).toHaveClass("aspect-[4/5]");
    });
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
  });

  it("넘기면_번호가_바뀐다", () => {
    render(<PhotoCarousel photos={photos(3)} version={4} />);
    const region = screen.getByRole("region", { name: "사진" });
    Object.defineProperty(region, "clientWidth", {
      configurable: true,
      value: 300,
    });
    Object.defineProperty(region, "scrollLeft", {
      configurable: true,
      value: 300,
    });
    fireEvent.scroll(region);
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
  });

  it("한_장이면_번호를_숨긴다", () => {
    render(<PhotoCarousel photos={photos(1)} version={4} />);
    expect(screen.getAllByRole("img")).toHaveLength(1);
    expect(screen.queryByText("1 / 1")).not.toBeInTheDocument();
  });
});
