import { useEffect, useMemo, useState } from "react";

const API_PORT = 9090;
const apiBase = `${window.location.protocol}//${window.location.hostname}:${API_PORT}`;
const DEFAULT_DEVICE = "bedroomlight";

type StatusTone = "idle" | "busy" | "success" | "error";
type ControlMode = "rgb" | "temperature" | "transition";

type DeviceInfo = {
  name: string;
  refName: string;
  ip: string;
  offlineDetection: boolean;
  online: boolean;
  lastSeen: string;
};

type DeviceState = {
  name: string;
  refName: string;
  online: boolean;
  state?: Record<string, unknown>;
  error?: string;
};

type Status = {
  tone: StatusTone;
  text: string;
};

type ControlValues = {
  brightness: string;
  temperature: string;
  red: string;
  green: string;
  blue: string;
  transition: string;
};

const fallbackValues: ControlValues = {
  brightness: "50",
  temperature: "65",
  red: "255",
  green: "160",
  blue: "60",
  transition: "50",
};

const colourFields: Array<["red" | "green" | "blue", string]> = [
  ["red", "Red"],
  ["green", "Green"],
  ["blue", "Blue"],
];

function kelvinToPercent(value: unknown) {
  if (typeof value !== "number") return "";
  const percent = Math.round(((value - 2200) / (6500 - 2200)) * 100);
  return String(Math.max(0, Math.min(100, percent)));
}

function numberString(value: unknown, fallback: string) {
  return typeof value === "number" ? String(Math.round(value)) : fallback;
}

function modeFromState(state?: Record<string, unknown>): ControlMode {
  if (!state) return "temperature";
  if (typeof state.r === "number" || typeof state.g === "number" || typeof state.b === "number") return "rgb";
  if (typeof state.temp === "number") return "temperature";
  return "temperature";
}

function valuesFromState(state?: Record<string, unknown>): ControlValues {
  return {
    brightness: numberString(state?.dimming, fallbackValues.brightness),
    temperature: kelvinToPercent(state?.temp) || fallbackValues.temperature,
    red: numberString(state?.r, fallbackValues.red),
    green: numberString(state?.g, fallbackValues.green),
    blue: numberString(state?.b, fallbackValues.blue),
    transition: numberString(state?.dimming, fallbackValues.transition),
  };
}

function powerFromState(state?: Record<string, unknown>) {
  return state?.state !== false;
}

export function SmartDeviceDashboard() {
  const [devices, setDevices] = useState<string[]>([]);
  const [deviceDetails, setDeviceDetails] = useState<DeviceInfo[]>([]);
  const [deviceStates, setDeviceStates] = useState<DeviceState[]>([]);
  const [selectedDevice, setSelectedDevice] = useState("");
  const [mode, setMode] = useState<ControlMode>("temperature");
  const [values, setValues] = useState<ControlValues>(fallbackValues);
  const [powerOn, setPowerOn] = useState(true);
  const [status, setStatus] = useState<Status>({ tone: "idle", text: "Ready" });
  const [loading, setLoading] = useState(true);

  const selectedState = deviceStates.find((item) => item.name === selectedDevice);
  const selectedDetails = deviceDetails.find((item) => item.name === selectedDevice);
  const isReachable = Boolean(selectedDevice && selectedState?.online);
  const selectedError = selectedState?.error;

  const deviceLabel = useMemo(() => {
    if (!selectedDevice) return "No device selected";
    if (!selectedDetails) return selectedDevice;
    return `${selectedDetails.name} (${selectedDetails.ip})`;
  }, [selectedDetails, selectedDevice]);

  useEffect(() => {
    Promise.all([
      fetch(`${apiBase}/devices`).then((response) => response.json() as Promise<string[]>),
      fetch(`${apiBase}/devices/details`)
        .then((response) => (response.ok ? (response.json() as Promise<DeviceInfo[]>) : []))
        .catch(() => []),
      fetch(`${apiBase}/device-states`)
        .then((response) => (response.ok ? (response.json() as Promise<DeviceState[]>) : []))
        .catch(() => []),
    ])
      .then(([deviceList, details, states]) => {
        const firstDevice = deviceList.includes(DEFAULT_DEVICE) ? DEFAULT_DEVICE : deviceList[0] ?? "";
        const firstState = states.find((item) => item.name === firstDevice)?.state;

        setDevices(deviceList);
        setDeviceDetails(details);
        setDeviceStates(states);
        setSelectedDevice(firstDevice);
        setMode(modeFromState(firstState));
        setValues(valuesFromState(firstState));
        setPowerOn(powerFromState(firstState));
        setStatus({ tone: "idle", text: deviceList.length ? "Ready" : "No devices returned by the manager" });
      })
      .catch((error: Error) => {
        setStatus({ tone: "error", text: `Could not reach SmartDeviceManager on ${apiBase}: ${error.message}` });
      })
      .finally(() => setLoading(false));
  }, []);

  function chooseDevice(deviceName: string) {
    const nextState = deviceStates.find((item) => item.name === deviceName)?.state;

    setSelectedDevice(deviceName);
    setMode(modeFromState(nextState));
    setValues(valuesFromState(nextState));
    setPowerOn(powerFromState(nextState));
  }

  function updateValue(name: keyof ControlValues, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
  }

  function sendPower(nextPower: boolean) {
    if (!selectedDevice || !isReachable) return;

    setPowerOn(nextPower);
    setStatus({ tone: "busy", text: nextPower ? "Turning on..." : "Turning off..." });
    fetch(`${apiBase}/command`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: selectedDevice,
        command: nextPower ? "on" : "off",
        params: [],
      }),
    })
      .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
      .then((result) => {
        if (!result.ok) setPowerOn(!nextPower);
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `${selectedDevice} is ${nextPower ? "on" : "off"}` : result.text.trim(),
        });
      })
      .catch((error: Error) => {
        setPowerOn(!nextPower);
        setStatus({ tone: "error", text: error.message });
      });
  }

  function applyValues() {
    if (!selectedDevice || !isReachable) return;

    setStatus({ tone: "busy", text: "Applying settings..." });
    fetch(`${apiBase}/custom-command`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: selectedDevice,
        brightness: Number(values.brightness),
        temperature: Number(values.temperature),
        red: Number(values.red),
        green: Number(values.green),
        blue: Number(values.blue),
        colourMode: mode === "rgb",
        transitionMode: mode === "transition",
        transition: Number(values.transition),
      }),
    })
      .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
      .then((result) => {
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `Settings applied to ${selectedDevice}` : result.text.trim(),
        });
      })
      .catch((error: Error) => setStatus({ tone: "error", text: error.message }));
  }

  function modePanelClass(panelMode: ControlMode) {
    return panelMode === mode ? "mode-panel active" : "mode-panel disabled";
  }

  function modeTabClass(tabMode: ControlMode) {
    return tabMode === mode ? "mode-tab selected" : "mode-tab inactive";
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Port 9091</p>
          <h1>Smart Device Manager</h1>
        </div>
        <div className={`status ${status.tone}`}>{status.text}</div>
      </header>

      <main className="simple-dashboard">
        <section className="device-list" aria-label="Devices">
          {devices.map((device) => {
            const liveState = deviceStates.find((item) => item.name === device);
            const reachable = liveState?.online;

            return (
              <button
                type="button"
                key={device}
                className={device === selectedDevice ? "device selected" : "device"}
                onClick={() => chooseDevice(device)}
              >
                <span>{device}</span>
                <small className={reachable ? "online-text" : "offline-text"}>
                  {reachable ? "Online" : "Offline"}
                </small>
              </button>
            );
          })}
          {!loading && devices.length === 0 ? <p className="empty">No devices found</p> : null}
        </section>

        <section className={isReachable ? "dashboard-card" : "dashboard-card unreachable"}>
          <div className="device-summary">
            <div>
              <p className="eyebrow">Selected device</p>
              <h2>{deviceLabel}</h2>
              <p className={isReachable ? "online-text" : "offline-text"}>
                {isReachable ? "Reachable" : selectedError || "Unreachable"}
              </p>
            </div>

            <label className="power-switch">
              <input
                type="checkbox"
                checked={powerOn}
                disabled={!isReachable}
                onChange={(event) => sendPower(event.target.checked)}
              />
              <span>{powerOn ? "On" : "Off"}</span>
            </label>
          </div>

          <div className="mode-tabs" aria-label="Control mode">
            {(["rgb", "temperature", "transition"] as ControlMode[]).map((item) => (
              <button
                type="button"
                key={item}
                className={modeTabClass(item)}
                disabled={!isReachable}
                onClick={() => setMode(item)}
              >
                {item === "rgb" ? "RGB" : item[0].toUpperCase() + item.slice(1)}
              </button>
            ))}
          </div>

          <div className="slider-stack">
            <section className={modePanelClass("rgb")} aria-label="RGB sliders">
              <h2>RGB</h2>
              {colourFields.map(([name, label]) => (
                <SliderField
                  key={name}
                  label={label}
                  max={255}
                  value={values[name]}
                  disabled={!isReachable || mode !== "rgb"}
                  onChange={(value) => updateValue(name, value)}
                />
              ))}
            </section>

            <section className={modePanelClass("temperature")} aria-label="Temperature slider">
              <h2>Temperature</h2>
              <SliderField
                label="Temperature"
                max={100}
                unit="%"
                value={values.temperature}
                disabled={!isReachable || mode !== "temperature"}
                onChange={(value) => updateValue("temperature", value)}
              />
              <SliderField
                label="Brightness"
                max={100}
                unit="%"
                value={values.brightness}
                disabled={!isReachable || mode !== "temperature"}
                onChange={(value) => updateValue("brightness", value)}
              />
            </section>

            <section className={modePanelClass("transition")} aria-label="Transition slider">
              <h2>Transition</h2>
              <SliderField
                label="Transition"
                max={100}
                unit="%"
                value={values.transition}
                disabled={!isReachable || mode !== "transition"}
                onChange={(value) => updateValue("transition", value)}
              />
              <SliderField
                label="Brightness"
                max={100}
                unit="%"
                value={values.brightness}
                disabled={!isReachable || mode !== "transition"}
                onChange={(value) => updateValue("brightness", value)}
              />
            </section>
          </div>

          <button className="custom-send" type="button" disabled={!isReachable} onClick={applyValues}>
            Apply settings
          </button>
        </section>
      </main>
    </div>
  );
}

type SliderFieldProps = {
  label: string;
  min?: number;
  max: number;
  unit?: string;
  value: string;
  disabled: boolean;
  onChange: (value: string) => void;
};

function SliderField({ label, min = 0, max, unit, value, disabled, onChange }: SliderFieldProps) {
  return (
    <label className="field">
      <span>
        {label}
        {unit ? ` (${unit})` : ""}
      </span>
      <div className="input-row">
        <input
          type="range"
          min={min}
          max={max}
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
        <input
          className="number-entry"
          type="number"
          min={min}
          max={max}
          value={value}
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
        />
      </div>
    </label>
  );
}
