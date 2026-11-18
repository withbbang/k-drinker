"use client";

import { useState } from "react";

import { SearchCombobox } from "@/components/post-edit/SearchCombobox";
import { useBrewerySearch } from "@/hooks/use-brewery-search";
import type { BreweryResponse } from "@/types/api/brewery";

export type BreweryFields = {
  address: string;
  phone: string;
  homepage: string;
  instagramHandle: string;
  otherSns: string;
};

type BreweryPickerProps = {
  disabled: boolean;
  brewery: BreweryResponse | null;
  fields: BreweryFields;
  onPick: (brewery: BreweryResponse) => void;
  onFieldsChange: (fields: BreweryFields) => void;
};

const INPUT =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-40";

const FIELD_LABELS: [keyof BreweryFields, string][] = [
  ["address", "주소"],
  ["phone", "전화"],
  ["homepage", "홈페이지"],
  ["instagramHandle", "인스타그램"],
  ["otherSns", "기타 SNS"],
];

export function BreweryPicker({
  disabled,
  brewery,
  fields,
  onPick,
  onFieldsChange,
}: BreweryPickerProps) {
  const [q, setQ] = useState("");
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
        <>
          {FIELD_LABELS.map(([key, label]) => (
            <label key={key} className="flex flex-col gap-1">
              <span className="text-sm">{label}</span>
              <input
                type="text"
                value={fields[key]}
                onChange={(event) =>
                  onFieldsChange({ ...fields, [key]: event.target.value })
                }
                className={INPUT}
              />
            </label>
          ))}
          <p className="text-sm text-neutral-700">
            고치면 다른 게시물에도 반영됩니다
          </p>
        </>
      )}
    </fieldset>
  );
}
