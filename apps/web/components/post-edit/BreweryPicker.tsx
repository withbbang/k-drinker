"use client";

import { useState } from "react";

import { BrewerySheet } from "@/components/post-edit/BrewerySheet";
import { SearchCombobox } from "@/components/post-edit/SearchCombobox";
import { useBrewerySearch } from "@/hooks/use-brewery-search";
import type { BreweryResponse } from "@/types/api/brewery";

type BreweryPickerProps = {
  disabled: boolean;
  brewery: BreweryResponse | null;
  onPick: (brewery: BreweryResponse) => void;
};

const INPUT =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40";

export function BreweryPicker({
  disabled,
  brewery,
  onPick,
}: BreweryPickerProps) {
  const [q, setQ] = useState("");
  const [sheetOpen, setSheetOpen] = useState(false);
  const search = useBrewerySearch(q);

  return (
    <fieldset className="flex flex-col gap-2" disabled={disabled}>
      <legend className="text-sm font-medium">양조장</legend>
      {disabled && (
        <p className="text-sm text-neutral-700">술을 먼저 정해 주세요</p>
      )}
      <p className="text-sm text-neutral-700">
        {brewery
          ? `선택한 양조장: ${brewery.name}`
          : "정해진 양조장이 없습니다."}
      </p>
      <SearchCombobox
        label="양조장 검색"
        items={search.data?.items ?? []}
        getKey={(item) => item.id}
        renderItem={(item) => item.name}
        selectedKey={brewery?.id ?? null}
        onPick={onPick}
        query={q}
        onQueryChange={setQ}
        inputClassName={INPUT}
      />
      {brewery && (
        <button
          type="button"
          onClick={() => setSheetOpen(true)}
          className="self-start rounded-md border border-neutral-300 px-3 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
        >
          양조장 정보 고치기
        </button>
      )}
      {brewery && sheetOpen && (
        <BrewerySheet brewery={brewery} onClose={() => setSheetOpen(false)} />
      )}
    </fieldset>
  );
}
