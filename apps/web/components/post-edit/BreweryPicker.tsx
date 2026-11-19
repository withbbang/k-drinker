"use client";

import { useState } from "react";

import { BrewerySheet } from "@/components/post-edit/BrewerySheet";
import { SearchCombobox } from "@/components/post-edit/SearchCombobox";
import { useBrewerySearch } from "@/hooks/use-brewery-search";
import { ApiError } from "@/lib/api";
import type { BreweryResponse } from "@/types/api/brewery";

type BreweryPickerProps = {
  disabled: boolean;
  brewery: BreweryResponse | null;
  // 따로 고른 양조장이면 true. 술의 기본 양조장은 지울 게 없으므로 × 를 숨긴다.
  clearable: boolean;
  // null이면 고른 양조장을 비워 술의 기본 양조장으로 돌아간다.
  onPick: (brewery: BreweryResponse | null) => void;
};

export function BreweryPicker({
  disabled,
  brewery,
  clearable,
  onPick,
}: BreweryPickerProps) {
  const [q, setQ] = useState("");
  const [sheetOpen, setSheetOpen] = useState(false);
  const search = useBrewerySearch(q);

  return (
    <fieldset className="flex flex-col gap-2" disabled={disabled}>
      <legend className="text-sm font-medium">양조장</legend>
      {disabled && <p className="text-sm text-muted">술을 먼저 정해 주세요</p>}
      {!brewery && (
        <p className="text-sm text-muted">정해진 양조장이 없어요.</p>
      )}
      <SearchCombobox
        label="양조장 검색"
        items={search.data?.items ?? []}
        getKey={(item) => item.id}
        renderItem={(item) => item.name}
        selectedKey={brewery?.id ?? null}
        onPick={onPick}
        query={q}
        onQueryChange={setQ}
        selectedLabel={brewery?.name ?? null}
        onClear={clearable ? () => onPick(null) : undefined}
        errorText={
          search.isError
            ? search.error instanceof ApiError
              ? search.error.message
              : "양조장을 검색하지 못했어요."
            : undefined
        }
        emptyText={
          search.isSuccess ? `‘${q.trim()}’에 대한 양조장이 없어요.` : undefined
        }
      />
      {brewery && (
        <button
          type="button"
          onClick={() => setSheetOpen(true)}
          className="min-h-11 self-start rounded-field border border-line px-3 text-sm font-semibold text-text focus-visible:outline-2 focus-visible:outline-accent"
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
