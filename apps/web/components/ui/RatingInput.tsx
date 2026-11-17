"use client";

const STEPS = Array.from({ length: 10 }, (_, i) => (i + 1) / 2);

type RatingInputProps = {
  value: number | null;
  onChange: (value: number | null) => void;
};

export function RatingInput({ value, onChange }: RatingInputProps) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-sm font-medium">별점</span>
      <select
        value={value === null ? "" : value.toFixed(1)}
        onChange={(event) =>
          onChange(
            event.target.value === "" ? null : Number(event.target.value),
          )
        }
        className="rounded-md border border-neutral-300 bg-white px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900"
      >
        <option value="">별점 없음</option>
        {STEPS.map((step) => (
          <option key={step} value={step.toFixed(1)}>
            {step.toFixed(1)}
          </option>
        ))}
      </select>
    </label>
  );
}
