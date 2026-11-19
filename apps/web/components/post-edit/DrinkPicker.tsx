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
  // 새 이름 칸에서 포커스가 나갔을 때(되돌리기 묶음을 끊는 데 쓴다).
  onNewNameBlur?: () => void;
};

const NONE: DrinkChoice = {
  drinkId: null,
  drinkName: null,
  breweryId: null,
  newDrinkName: "",
};

const INPUT =
  "min-h-11 rounded-field border border-line bg-surface px-3 text-text focus-visible:outline-2 focus-visible:outline-accent";

export function DrinkPicker({
  value,
  onChange,
  onNewNameBlur,
}: DrinkPickerProps) {
  const [q, setQ] = useState("");
  const search = useDrinkSearch(q);
  const newName = value.newDrinkName.trim();

  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-sm font-medium">술</legend>
      {newName === "" && value.drinkId === null && (
        <p className="text-sm text-muted">정해진 술이 없습니다.</p>
      )}
      <SearchCombobox
        label="술 검색"
        items={search.data ?? []}
        getKey={(drink) => drink.id}
        renderItem={(drink) =>
          `${drink.name}${drink.breweryName ? ` · ${drink.breweryName}` : ""}`
        }
        selectedKey={newName === "" ? value.drinkId : null}
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
        selectedLabel={
          newName !== ""
            ? newName
            : value.drinkId !== null
              ? value.drinkName
              : null
        }
        onClear={() => onChange(NONE)}
      />
      <label className="flex flex-col gap-1">
        <span className="text-sm text-muted">새 이름으로</span>
        <input
          type="text"
          value={value.newDrinkName}
          onChange={(event) =>
            onChange({ ...value, newDrinkName: event.target.value })
          }
          onBlur={onNewNameBlur}
          className={INPUT}
        />
      </label>
    </fieldset>
  );
}
