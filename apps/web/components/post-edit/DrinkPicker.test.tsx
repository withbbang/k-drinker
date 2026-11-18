import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  DrinkPicker,
  type DrinkChoice,
} from "@/components/post-edit/DrinkPicker";
import { QueryProvider } from "@/components/providers/QueryProvider";

const { apiFetchMock } = vi.hoisted(() => ({ apiFetchMock: vi.fn() }));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts/7/edit",
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  apiFetch: apiFetchMock,
}));

const START: DrinkChoice = {
  drinkId: null,
  drinkName: null,
  breweryId: null,
  newDrinkName: "",
};

const onChange = vi.fn();
const onSubmit = vi.fn((event: { preventDefault: () => void }) =>
  event.preventDefault(),
);

function Harness() {
  const [value, setValue] = useState(START);
  return (
    <form onSubmit={onSubmit}>
      <DrinkPicker
        value={value}
        onChange={(next) => {
          onChange(next);
          setValue(next);
        }}
      />
    </form>
  );
}

function setup() {
  apiFetchMock.mockResolvedValue([
    { id: 11, name: "청주", breweryId: 6, breweryName: "배상면주가" },
    { id: 12, name: "탁주", breweryId: null, breweryName: null },
  ]);
  render(
    <QueryProvider>
      <Harness />
    </QueryProvider>,
  );
}

const search = () => screen.getByRole("combobox", { name: "술 검색" });

describe("DrinkPicker", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    onChange.mockReset();
    onSubmit.mockClear();
  });

  it("picks the first result on Enter without submitting the form", async () => {
    setup();
    await userEvent.type(search(), "청");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{Enter}");

    expect(onSubmit).not.toHaveBeenCalled();
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ drinkId: 11, drinkName: "청주" }),
    );
  });

  it("does not submit the form on Enter without results", async () => {
    setup();
    await userEvent.type(search(), "{Enter}");
    expect(onSubmit).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("clears the query and closes the list after a click", async () => {
    setup();
    await userEvent.type(search(), "청");
    await userEvent.click(await screen.findByRole("option", { name: /청주/ }));

    expect(search()).toHaveValue("");
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(screen.getByText("선택한 술: 청주")).toBeInTheDocument();
  });

  it("marks the chosen drink as selected on a later search", async () => {
    setup();
    await userEvent.type(search(), "청");
    await userEvent.click(await screen.findByRole("option", { name: /청주/ }));
    await userEvent.type(search(), "청");

    expect(await screen.findByRole("option", { name: /청주/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(screen.getByRole("option", { name: "탁주" })).toHaveAttribute(
      "aria-selected",
      "false",
    );
  });

  it("moves the active option with arrow keys and picks it on Enter", async () => {
    setup();
    await userEvent.type(search(), "청");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}");
    const second = screen.getByRole("option", { name: "탁주" });
    expect(search()).toHaveAttribute("aria-activedescendant", second.id);

    await userEvent.keyboard("{ArrowUp}");
    const first = screen.getByRole("option", { name: /청주/ });
    expect(search()).toHaveAttribute("aria-activedescendant", first.id);

    await userEvent.keyboard("{ArrowDown}{Enter}");
    expect(onChange).toHaveBeenCalledWith(
      expect.objectContaining({ drinkId: 12, drinkName: "탁주" }),
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
