import { RadioButton, RadioButtonGroup } from "@salt-ds/core";
import type { ControlMode } from "./types";

type ModeHeaderProps = {
  label: string;
  mode: ControlMode;
  selectedMode: ControlMode;
  disabled: boolean;
  onSelectMode: (mode: ControlMode) => void;
};

export function ModeHeader({ label, mode, selectedMode, disabled, onSelectMode }: ModeHeaderProps) {
  return (
    <RadioButtonGroup
      className="mode-header"
      direction="horizontal"
      name="control-mode"
      value={selectedMode}
      disabled={disabled}
      onChange={(event) => onSelectMode(event.target.value as ControlMode)}
    >
      <RadioButton name="control-mode" value={mode} checked={selectedMode === mode} label={label} />
    </RadioButtonGroup>
  );
}
