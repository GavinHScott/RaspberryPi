import { ModeHeader } from "./ModeHeader";
import { SliderField } from "./SliderField";
import type { ControlMode, ControlValues } from "./types";

type TransitionSliderGroupProps = {
  selectedMode: ControlMode;
  values: ControlValues;
  disabled: boolean;
  onSelectMode: (mode: ControlMode) => void;
  onChangeValue: (name: "transition", value: string) => void;
};

export function TransitionSliderGroup({
  selectedMode,
  values,
  disabled,
  onSelectMode,
  onChangeValue,
}: TransitionSliderGroupProps) {
  const active = selectedMode === "transition";

  return (
    <section className={active ? "mode-panel active" : "mode-panel disabled"} aria-label="Transition slider">
      <ModeHeader
        label="Transition"
        mode="transition"
        selectedMode={selectedMode}
        disabled={disabled}
        onSelectMode={onSelectMode}
      />
      <SliderField
        label="Transition"
        max={100}
        unit="%"
        value={values.transition}
        disabled={disabled || !active}
        onChange={(value) => onChangeValue("transition", value)}
      />
    </section>
  );
}
