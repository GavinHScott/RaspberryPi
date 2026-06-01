export type ControlMode = "rgb" | "temperature" | "transition";

export type ControlValues = {
  brightness: string;
  temperature: string;
  red: string;
  green: string;
  blue: string;
  transition: string;
};

export type ControlValueName = keyof ControlValues;
