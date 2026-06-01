import { ModeHeader } from "./ModeHeader";
import { SliderField } from "./SliderField";
import type { ControlMode, ControlValues } from "./types";

type TemperatureSliderGroupProps = {
  selectedMode: ControlMode;
  values: ControlValues;
  disabled: boolean;
  onSelectMode: (mode: ControlMode) => void;
  onChangeValue: (name: "temperature" | "brightness", value: string) => void;
};

export function TemperatureSliderGroup({
  selectedMode,
  values,
  disabled,
  onSelectMode,
  onChangeValue,
}: TemperatureSliderGroupProps) {
  const active = selectedMode === "temperature";
  const fieldDisabled = disabled || !active;

  return (
    <section className={active ? "mode-panel active" : "mode-panel disabled"} aria-label="Temperature slider">
      <ModeHeader
        label="Temperature"
        mode="temperature"
        selectedMode={selectedMode}
        disabled={disabled}
        onSelectMode={onSelectMode}
      />
      <SliderField
        label="Temperature"
        max={100}
        unit="%"
        value={values.temperature}
        disabled={fieldDisabled}
        onChange={(value) => onChangeValue("temperature", value)}
      />
      <SliderField
        label="Brightness"
        max={100}
        unit="%"
        value={values.brightness}
        disabled={fieldDisabled}
        onChange={(value) => onChangeValue("brightness", value)}
      />
    </section>
  );
}
