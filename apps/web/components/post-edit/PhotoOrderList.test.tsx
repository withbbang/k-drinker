import { useState } from "react";

import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { PhotoOrderList } from "@/components/post-edit/PhotoOrderList";
import type { PostPhotoResponse } from "@/types/api/post";

function make(count: number): PostPhotoResponse[] {
  return Array.from({ length: count }, (_, i) => ({
    id: i + 1,
    position: i,
    url: `/media/${i + 1}.jpg`,
    fitMode: "CROP" as const,
  }));
}

function Harness({ initial }: { initial: PostPhotoResponse[] }) {
  const [photos, setPhotos] = useState(initial);
  return (
    <>
      <PhotoOrderList photos={photos} version={3} onChange={setPhotos} />
      <output data-testid="order">
        {photos.map((p) => `${p.id}:${p.fitMode}`).join(",")}
      </output>
    </>
  );
}

describe("PhotoOrderList", () => {
  it("moves a photo up", async () => {
    render(<Harness initial={make(3)} />);
    await userEvent.click(screen.getAllByRole("button", { name: "위로" })[1]);
    expect(screen.getByTestId("order")).toHaveTextContent(
      "2:CROP,1:CROP,3:CROP",
    );
  });

  it("cannot delete the last photo", async () => {
    render(<Harness initial={make(2)} />);
    await userEvent.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    expect(screen.getByTestId("order")).toHaveTextContent("2:CROP");
    expect(screen.getByRole("button", { name: "삭제" })).toBeDisabled();
  });

  it("toggles padding", async () => {
    render(<Harness initial={make(2)} />);
    await userEvent.click(screen.getAllByLabelText("여백 넣기")[1]);
    expect(screen.getByTestId("order")).toHaveTextContent("1:CROP,2:PAD");
  });

  it("busts the image cache with the version", () => {
    render(<Harness initial={make(1)} />);
    expect(screen.getByAltText("사진 1")).toHaveAttribute(
      "src",
      "/media/1.jpg?v=3",
    );
  });
});
