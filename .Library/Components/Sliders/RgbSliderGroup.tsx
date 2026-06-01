import { ModeHeader } from "./ModeHeader";
import { SliderField } from "./SliderField";
import type { ControlMode, ControlValues } from "./types";

const colourFields: Array<["red" | "green" | "blue", string]> = [
  ["red", "Red"],
  ["green", "Green"],
  ["blue", "Blue"],
];

type RgbSliderGroupProps = {
  selectedMode: ControlMode;
  values: ControlValues;
  disabled: boolean;
  onSelectMode: (mode: ControlMode) => void;
  onChangeValue: (name: "red" | "green" | "blue", value: string) => void;
};

export function RgbSliderGroup({
  selectedMode,
  values,
  disabled,
  onSelectMode,
  onChangeValue,
}: RgbSliderGroupProps) {
  const active = selectedMode === "rgb";

  return (
    <section className={active ? "mode-panel active" : "mode-panel disabled"} aria-label="RGB sliders">
      <ModeHeader label="RGB" mode="rgb" selectedMode={selectedMode} disabled={disabled} onSelectMode={onSelectMode} />
      {colourFields.map(([name, label]) => (
        <SliderField
          key={name}
          label={label}
          max={255}
          value={values[name]}
          disabled={disabled || !active}
          onChange={(value) => onChangeValue(name, value)}
        />
      ))}
    </section>
  );
}
