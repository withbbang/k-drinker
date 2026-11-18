import { describe, expect, it } from "vitest";

import {
  fromSaved,
  newItems,
  toUpdatePhotos,
} from "@/components/post-edit/photo-items";

const file = (name: string) => new File(["x"], name, { type: "image/jpeg" });

describe("photo-items", () => {
  it("keys saved photos by id", () => {
    // given / when
    const items = fromSaved([
      { id: 3, position: 0, url: "/m/3.jpg", fitMode: "PAD" },
    ]);

    // then
    expect(items).toEqual([
      { key: "saved-3", kind: "saved", id: 3, url: "/m/3.jpg", fitMode: "PAD" },
    ]);
  });

  it("keys new photos by sequence with CROP", () => {
    // given
    const a = file("a.jpg");
    const b = file("b.jpg");

    // when
    const items = newItems([a, b], 5);

    // then
    expect(items).toEqual([
      { key: "new-5", kind: "new", file: a, fitMode: "CROP" },
      { key: "new-6", kind: "new", file: b, fitMode: "CROP" },
    ]);
  });

  it("numbers new photos in order of appearance", () => {
    // given
    const a = file("a.jpg");
    const b = file("b.jpg");
    const [saved1, saved2] = fromSaved([
      { id: 1, position: 0, url: "/m/1.jpg", fitMode: "CROP" },
      { id: 2, position: 1, url: "/m/2.jpg", fitMode: "PAD" },
    ]);
    const [newA, newB] = newItems([a, b], 0);

    // when
    const result = toUpdatePhotos([
      saved1,
      newA,
      saved2,
      { ...newB, fitMode: "PAD" },
    ]);

    // then
    expect(result.photos).toEqual([
      { id: 1, fitMode: "CROP" },
      { newIndex: 0, fitMode: "CROP" },
      { id: 2, fitMode: "PAD" },
      { newIndex: 1, fitMode: "PAD" },
    ]);
    expect(result.newPhotos).toEqual([a, b]);
  });
});
