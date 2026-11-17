import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { RatingInput } from "@/components/ui/RatingInput";

describe("RatingInput", () => {
  it("offers no rating and half steps up to five", () => {
    // when
    render(<RatingInput value={null} onChange={() => {}} />);

    // then
    const options = screen
      .getByRole("combobox", { name: "별점" })
      .querySelectorAll("option");
    expect(Array.from(options).map((o) => o.textContent)).toEqual([
      "별점 없음",
      "0.5",
      "1.0",
      "1.5",
      "2.0",
      "2.5",
      "3.0",
      "3.5",
      "4.0",
      "4.5",
      "5.0",
    ]);
  });

  it("reports null when no rating is chosen", async () => {
    // given
    const onChange = vi.fn();
    render(<RatingInput value={4.5} onChange={onChange} />);

    // when
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "별점" }),
      "별점 없음",
    );

    // then
    expect(onChange).toHaveBeenCalledWith(null);
  });

  it("reports the chosen number", async () => {
    // given
    const onChange = vi.fn();
    render(<RatingInput value={null} onChange={onChange} />);

    // when
    await userEvent.selectOptions(
      screen.getByRole("combobox", { name: "별점" }),
      "4.5",
    );

    // then
    expect(onChange).toHaveBeenCalledWith(4.5);
  });
});
