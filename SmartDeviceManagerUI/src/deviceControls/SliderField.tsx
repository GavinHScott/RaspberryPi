import { NumberInput, Slider } from "@salt-ds/core";

type SliderFieldProps = {
  label: string;
  min?: number;
  max: number;
  unit?: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
};

export function SliderField({ label, min = 0, max, unit, value, disabled, onChange }: SliderFieldProps) {
  const numericValue = Number(value);

  return (
    <label className="field">
      <span>
        {label}
        {unit ? ` (${unit})` : ""}
      </span>
      <div className="input-row">
        <Slider
          min={min}
          max={max}
          value={Number.isFinite(numericValue) ? numericValue : min}
          disabled={disabled}
          aria-label={label}
          showTooltip
          onChange={(_, nextValue) => onChange(String(nextValue))}
        />
        <NumberInput
          className="number-entry"
          bordered
          hideButtons
          min={min}
          max={max}
          value={value}
          disabled={disabled}
          textAlign="right"
          inputProps={{ "aria-label": `${label} value` }}
          onChange={(_, nextValue) => onChange(nextValue)}
        />
      </div>
    </label>
  );
}
