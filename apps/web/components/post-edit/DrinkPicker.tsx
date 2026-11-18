"use client";

import { useState } from "react";

import { SearchCombobox } from "@/components/post-edit/SearchCombobox";
import { useDrinkSearch } from "@/hooks/use-drink-search";

export type DrinkChoice = {
  drinkId: number | null;
  drinkName: string | null;
  // 고른 술의 양조장. 술을 바꾸면 이 양조장을 현재 양조장으로 보여 준다.
  breweryId: number | null;
  newDrinkName: string;
};

type DrinkPickerProps = {
  value: DrinkChoice;
  onChange: (value: DrinkChoice) => void;
};

const INPUT =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900";

export function DrinkPicker({ value, onChange }: DrinkPickerProps) {
  const [q, setQ] = useState("");
  const search = useDrinkSearch(q);

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium">술</legend>
      <p className="text-sm text-neutral-700">
        {value.newDrinkName.trim() !== ""
          ? `새 술: ${value.newDrinkName.trim()}`
          : value.drinkId !== null
            ? `선택한 술: ${value.drinkName}`
            : "정해진 술이 없습니다."}
      </p>
      <SearchCombobox
        label="술 검색"
        items={search.data ?? []}
        getKey={(drink) => drink.id}
        renderItem={(drink) =>
          `${drink.name}${drink.breweryName ? ` · ${drink.breweryName}` : ""}`
        }
        selectedKey={value.newDrinkName.trim() === "" ? value.drinkId : null}
        onPick={(drink) =>
          onChange({
            drinkId: drink.id,
            drinkName: drink.name,
            breweryId: drink.breweryId,
            newDrinkName: "",
          })
        }
        query={q}
        onQueryChange={setQ}
        inputClassName={INPUT}
      />
      <label className="flex flex-col gap-1">
        <span className="text-sm">새 이름으로</span>
        <input
          type="text"
          value={value.newDrinkName}
          onChange={(event) =>
            onChange({ ...value, newDrinkName: event.target.value })
          }
          className={INPUT}
        />
      </label>
    </fieldset>
  );
}
