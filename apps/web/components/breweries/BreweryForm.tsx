"use client";

import {
  useState,
  type FormEvent,
  type HTMLInputTypeAttribute,
  type HTMLInputAutoCompleteAttribute,
} from "react";

import { useRouter } from "next/navigation";

import { buttonClass } from "@/components/ui/button";
import { useSaveBrewery } from "@/hooks/use-breweries";
import { ApiError } from "@/lib/api";
import type { BreweryRequest, BreweryResponse } from "@/types/api/brewery";

type FieldName = keyof BreweryRequest;

type FieldDef = {
  name: FieldName;
  label: string;
  type?: HTMLInputTypeAttribute;
  inputMode?: "tel" | "url";
  autoComplete?: HTMLInputAutoCompleteAttribute;
  placeholder?: string;
};

const FIELDS: FieldDef[] = [
  { name: "name", label: "이름" },
  { name: "address", label: "주소" },
  {
    name: "phone",
    label: "전화",
    type: "tel",
    inputMode: "tel",
    autoComplete: "tel",
  },
  {
    name: "homepage",
    label: "홈페이지",
    type: "url",
    inputMode: "url",
    autoComplete: "url",
  },
  {
    name: "instagramHandle",
    label: "인스타그램",
    placeholder: "@ 없이 아이디만",
  },
  { name: "otherSns", label: "기타 SNS" },
];

const INPUT =
  "rounded-field border border-line bg-surface px-3 py-2.5 text-text placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent";

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
  // 아래에 붙는 저장 막대의 배경 토큰. 놓이는 면의 배경과 맞춘다.
  barClassName?: string;
  // 새로 만든 뒤 상세 화면으로 옮겨 왔으면 첫 화면에 저장 완료 문구를 보인다.
  justCreated?: boolean;
};

export function BreweryForm({
  brewery,
  onSaved,
  barClassName = "bg-bg",
  justCreated = false,
}: BreweryFormProps) {
  const [values, setValues] = useState(() => initial(brewery));
  const [savedId, setSavedId] = useState<number | null>(brewery?.id ?? null);
  const [nameError, setNameError] = useState(false);
  const save = useSaveBrewery(savedId);
  const router = useRouter();

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
          // 새로 만든 양조장은 상세 화면으로 옮긴다(시트·수정에서는 머문다).
          if (!brewery && !onSaved)
            router.replace(`/breweries/${saved.id}?saved=1`);
        },
      },
    );
  };

  const apiError = save.error instanceof ApiError ? save.error : null;
  const fieldReason = (name: FieldName) =>
    apiError?.fields.find((f) => f.field === name)?.reason;

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {FIELDS.map(({ name, label, ...inputProps }) => {
        const reason =
          name === "name" && nameError
            ? "이름을 입력해 주세요."
            : fieldReason(name);
        return (
          <label key={name} className="flex flex-col gap-1">
            <span className="text-sm text-muted">{label}</span>
            <input
              value={values[name]}
              onChange={(event) =>
                setValues({ ...values, [name]: event.target.value })
              }
              aria-invalid={reason ? true : undefined}
              className={INPUT}
              {...inputProps}
            />
            {reason && <span className="text-sm text-danger">{reason}</span>}
          </label>
        );
      })}
      {save.isError && (
        <p role="alert" className="text-sm text-danger">
          {apiError ? apiError.message : "저장하지 못했어요."}
        </p>
      )}
      {(save.isSuccess || (justCreated && save.isIdle)) && !onSaved && (
        <p role="status" className="text-sm text-accent">
          저장했어요. 다음 게시물부터 이 정보가 쓰여요.
        </p>
      )}
      <div
        className={`sticky bottom-0 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] ${barClassName}`}
      >
        <button
          type="submit"
          disabled={save.isPending}
          className={buttonClass("primary", "w-full")}
        >
          저장
        </button>
      </div>
    </form>
  );
}
