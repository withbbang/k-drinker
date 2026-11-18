import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { BreweryPicker } from "@/components/post-edit/BreweryPicker";
import { QueryProvider } from "@/components/providers/QueryProvider";
import type { BreweryResponse } from "@/types/api/brewery";

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

const make = (id: number, name: string): BreweryResponse => ({
  id,
  name,
  address: null,
  phone: null,
  homepage: null,
  instagramHandle: null,
  otherSns: null,
  source: "MANUAL",
  verified: false,
});

const FIELDS = {
  address: "",
  phone: "",
  homepage: "",
  instagramHandle: "",
  otherSns: "",
};

const onPick = vi.fn();
const onSubmit = vi.fn((event: { preventDefault: () => void }) =>
  event.preventDefault(),
);

function Harness() {
  const [brewery, setBrewery] = useState<BreweryResponse | null>(null);
  return (
    <form onSubmit={onSubmit}>
      <BreweryPicker
        disabled={false}
        brewery={brewery}
        fields={FIELDS}
        onPick={(next) => {
          onPick(next);
          setBrewery(next);
        }}
        onFieldsChange={() => undefined}
      />
    </form>
  );
}

function setup() {
  apiFetchMock.mockResolvedValue({
    items: [make(5, "국순당"), make(6, "배상면주가")],
    page: 0,
    size: 20,
    totalElements: 2,
    totalPages: 1,
  });
  render(
    <QueryProvider>
      <Harness />
    </QueryProvider>,
  );
}

const search = () => screen.getByRole("combobox", { name: "양조장 검색" });

describe("BreweryPicker", () => {
  beforeEach(() => {
    apiFetchMock.mockReset();
    onPick.mockReset();
    onSubmit.mockClear();
  });

  it("picks the first result on Enter without submitting the form", async () => {
    setup();
    await userEvent.type(search(), "국");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{Enter}");

    expect(onSubmit).not.toHaveBeenCalled();
    expect(onPick).toHaveBeenCalledWith(expect.objectContaining({ id: 5 }));
  });

  it("clears the query and closes the list after a click", async () => {
    setup();
    await userEvent.type(search(), "배");
    await userEvent.click(
      await screen.findByRole("option", { name: "배상면주가" }),
    );

    expect(search()).toHaveValue("");
    expect(screen.queryByRole("listbox")).toBeNull();
    expect(screen.getByText("선택한 양조장: 배상면주가")).toBeInTheDocument();
  });

  it("marks the chosen brewery as selected on a later search", async () => {
    setup();
    await userEvent.type(search(), "배");
    await userEvent.click(
      await screen.findByRole("option", { name: "배상면주가" }),
    );
    await userEvent.type(search(), "배");

    expect(
      await screen.findByRole("option", { name: "배상면주가" }),
    ).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("option", { name: "국순당" })).toHaveAttribute(
      "aria-selected",
      "false",
    );
  });

  it("moves the active option with arrow keys and picks it on Enter", async () => {
    setup();
    await userEvent.type(search(), "국");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}");
    const second = screen.getByRole("option", { name: "배상면주가" });
    expect(search()).toHaveAttribute("aria-activedescendant", second.id);

    await userEvent.keyboard("{ArrowUp}{ArrowDown}{Enter}");
    expect(onPick).toHaveBeenCalledWith(expect.objectContaining({ id: 6 }));
    expect(onSubmit).not.toHaveBeenCalled();
  });
});
