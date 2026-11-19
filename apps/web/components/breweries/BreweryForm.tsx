"use client";

import { useState, type FormEvent } from "react";

import { useSaveBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";
import type { BreweryRequest, BreweryResponse } from "@/types/api/brewery";

type FieldName = keyof BreweryRequest;

const FIELDS: [FieldName, string][] = [
  ["name", "이름"],
  ["address", "주소"],
  ["phone", "전화"],
  ["homepage", "홈페이지"],
  ["instagramHandle", "인스타그램"],
  ["otherSns", "기타 SNS"],
];

const INPUT =
  "rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900";

function initial(brewery?: BreweryResponse): Record<FieldName, string> {
  return {
    name: brewery?.name ?? "",
    address: brewery?.address ?? "",
    phone: brewery?.phone ?? "",
    homepage: brewery?.homepage ?? "",
    instagramHandle: brewery?.instagramHandle ?? "",
    otherSns: brewery?.otherSns ?? "",
  };
}

type BreweryFormProps = {
  brewery?: BreweryResponse;
  // 있으면 저장 성공 때 부르고, 저장 완료 표시는 하지 않는다.
  onSaved?: (saved: BreweryResponse) => void;
};

export function BreweryForm({ brewery, onSaved }: BreweryFormProps) {
  const [values, setValues] = useState(() => initial(brewery));
  const [savedId, setSavedId] = useState<number | null>(brewery?.id ?? null);
  const [nameError, setNameError] = useState(false);
  const save = useSaveBrewery(savedId);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (values.name.trim() === "") {
      setNameError(true);
      return;
    }
    setNameError(false);
    const orNull = (value: string) =>
      value.trim() === "" ? null : value.trim();
    save.mutate(
      {
        name: values.name.trim(),
        address: orNull(values.address),
        phone: orNull(values.phone),
        homepage: orNull(values.homepage),
        instagramHandle: orNull(values.instagramHandle),
        otherSns: orNull(values.otherSns),
      },
      // 새로 만든 뒤 다시 저장하면 중복 생성이 아니라 수정이 된다.
      {
        onSuccess: (saved) => {
          setSavedId(saved.id);
          onSaved?.(saved);
        },
      },
    );
  };

  const apiError = save.error instanceof ApiError ? save.error : null;
  const fieldReason = (name: FieldName) =>
    apiError?.fields.find((f) => f.field === name)?.reason;

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {FIELDS.map(([name, label]) => {
        const reason =
          name === "name" && nameError
            ? "이름을 입력해 주세요."
            : fieldReason(name);
        return (
          <label key={name} className="flex flex-col gap-1">
            <span className="text-sm">{label}</span>
            <input
              value={values[name]}
              onChange={(event) =>
                setValues({ ...values, [name]: event.target.value })
              }
              aria-invalid={reason ? true : undefined}
              className={INPUT}
            />
            {reason && <span className="text-sm text-red-700">{reason}</span>}
          </label>
        );
      })}
      {save.isError && (
        <p role="alert" className="text-sm text-red-700">
          {apiError ? apiError.message : "저장하지 못했습니다."}
        </p>
      )}
      {save.isSuccess && !onSaved && (
        <p role="status" className="text-sm text-green-800">
          저장했습니다. 다음 게시물부터 이 정보가 쓰입니다.
        </p>
      )}
      <button
        type="submit"
        disabled={save.isPending}
        className="rounded-md bg-neutral-900 px-4 py-3 text-white disabled:opacity-40"
      >
        저장
      </button>
    </form>
  );
}
