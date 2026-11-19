"use client";

const STEPS = Array.from({ length: 10 }, (_, i) => (i + 1) / 2);

type RatingInputProps = {
  value: number | null;
  onChange: (value: number | null) => void;
  disabled?: boolean;
};

export function RatingInput({ value, onChange, disabled }: RatingInputProps) {
  return (
    <label className="flex flex-col gap-2">
      <span className="text-sm font-medium text-text">별점</span>
      <select
        value={value === null ? "" : value.toFixed(1)}
        onChange={(event) =>
          onChange(
            event.target.value === "" ? null : Number(event.target.value),
          )
        }
        disabled={disabled}
        className="rounded-field border border-line bg-surface px-3 py-2.5 text-text focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
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
