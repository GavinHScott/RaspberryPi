import { useEffect, useMemo, useState } from "react";
import { Switch } from "@salt-ds/core";
import { DeviceSidebar } from "./DeviceSidebar";
import { RgbSliderGroup } from "./deviceControls/RgbSliderGroup";
import { TemperatureSliderGroup } from "./deviceControls/TemperatureSliderGroup";
import { TransitionSliderGroup } from "./deviceControls/TransitionSliderGroup";
import type { ControlMode, ControlValueName, ControlValues } from "./deviceControls/types";

const API_PORT = 9090;
const apiBase = `http://${window.location.hostname}:${API_PORT}`;
const DEFAULT_DEVICE = "bedroomlight";

type StatusTone = "idle" | "busy" | "success" | "error";
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

const fallbackValues: ControlValues = {
  brightness: "50",
  temperature: "65",
  red: "255",
  green: "160",
  blue: "60",
  transition: "50",
};

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
  const [isHeld, setIsHeld] = useState(false);
  const [status, setStatus] = useState<Status>({ tone: "idle", text: "Ready" });
  const [loading, setLoading] = useState(true);
  const [refreshingDevices, setRefreshingDevices] = useState(false);

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

  function refreshDevices() {
    setRefreshingDevices(true);
    fetch(`${apiBase}/devices/refresh`, { method: "POST" })
      .then((response) => response.json() as Promise<DeviceInfo[]>)
      .then((details) => {
        setDeviceDetails(details);
        setDeviceStates((currentStates) =>
          devices.map((deviceName) => {
            const refreshedDevice = details.find((item) => item.name === deviceName);
            const currentState = currentStates.find((item) => item.name === deviceName);

            return {
              name: deviceName,
              refName: refreshedDevice?.refName ?? currentState?.refName ?? deviceName,
              online: refreshedDevice?.online ?? currentState?.online ?? false,
              state: currentState?.state,
              error: refreshedDevice?.online ? undefined : currentState?.error,
            };
          }),
        );
      })
      .catch((error: Error) => {
        setStatus({ tone: "error", text: error.message });
      })
      .finally(() => setRefreshingDevices(false));
  }

  function buildControlPayload(nextMode: ControlMode, nextValues: ControlValues) {
    const payload: {
      name: string;
      brightness?: number;
      temperature: number;
      red: number;
      green: number;
      blue: number;
      colourMode: boolean;
      transitionMode: boolean;
      transition: number;
    } = {
      name: selectedDevice,
      temperature: Number(nextValues.temperature),
      red: Number(nextValues.red),
      green: Number(nextValues.green),
      blue: Number(nextValues.blue),
      colourMode: nextMode === "rgb",
      transitionMode: nextMode === "transition",
      transition: Number(nextValues.transition),
    };

    if (nextMode !== "transition") {
      payload.brightness = Number(nextValues.brightness);
    }

    return payload;
  }

  function sendPowerCommand(nextPower: boolean) {
    if (!selectedDevice || !isReachable) return Promise.resolve(false);

    setStatus({ tone: "busy", text: nextPower ? "Turning on..." : "Turning off..." });
    return fetch(`${apiBase}/command`, {
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
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `${selectedDevice} is ${nextPower ? "on" : "off"}` : result.text.trim(),
        });
        return result.ok;
      })
      .catch((error: Error) => {
        setStatus({ tone: "error", text: error.message });
        return false;
      });
  }

  function sendControlValues(nextMode = mode, nextValues = values) {
    if (!selectedDevice || !isReachable) return Promise.resolve(false);

    setStatus({ tone: "busy", text: "Updating..." });
    return fetch(`${apiBase}/custom-command`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(buildControlPayload(nextMode, nextValues)),
    })
      .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
      .then((result) => {
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `Updated ${selectedDevice}` : result.text.trim(),
        });
        return result.ok;
      })
      .catch((error: Error) => {
        setStatus({ tone: "error", text: error.message });
        return false;
      });
  }

  function updateValue(name: ControlValueName, value: string) {
    const nextValues = { ...values, [name]: value };
    setValues(nextValues);
    if (!isHeld) {
      sendControlValues(mode, nextValues);
    }
  }

  function chooseMode(nextMode: ControlMode) {
    setMode(nextMode);
    if (!isHeld) {
      sendControlValues(nextMode, values);
    }
  }

  function sendPower(nextPower: boolean) {
    const previousPower = powerOn;
    setPowerOn(nextPower);

    if (isHeld) return;

    sendPowerCommand(nextPower).then((ok) => {
      if (!ok) setPowerOn(previousPower);
    });
  }

  function setUpdateMode(updating: boolean) {
    if (!updating) {
      setIsHeld(true);
      return;
    }

    setIsHeld(false);
    if (!isReachable) return;

    if (powerOn) {
      sendControlValues(mode, values);
    } else {
      sendPowerCommand(false);
    }
  }

  return (
    <div className="app-shell">
      <div className="header-grid">
        <header className="topbar">
          <div>
            <h1>Smart Device Manager</h1>
          </div>
        </header>
      </div>

      <div className="content-grid">
        <DeviceSidebar
          devices={devices}
          deviceStates={deviceStates}
          selectedDevice={selectedDevice}
          loading={loading}
          refreshing={refreshingDevices}
          onSelectDevice={chooseDevice}
          onRefreshDevices={refreshDevices}
        />

        <main className="main-column">
        <section className={isReachable ? "dashboard-card" : "dashboard-card unreachable"}>
          <div className="device-summary">
            <div>
              <p className="eyebrow">Selected device</p>
              <h2>{deviceLabel}</h2>
              <p className={isReachable ? "online-text" : "offline-text"}>
                {isReachable ? "Reachable" : selectedError || "Unreachable"}
              </p>
            </div>

            <div className="top-controls">
              <Switch
                className="power-switch"
                checked={!isHeld}
                disabled={!isReachable}
                label={isHeld ? "Hold" : "Update"}
                onChange={(event) => setUpdateMode(event.target.checked)}
              />
              <Switch
                className="power-switch"
                checked={powerOn}
                disabled={!isReachable}
                label={powerOn ? "On" : "Off"}
                onChange={(event) => sendPower(event.target.checked)}
              />
            </div>
          </div>

          <div className="slider-stack">
            <RgbSliderGroup
              selectedMode={mode}
              values={values}
              disabled={!isReachable}
              onSelectMode={chooseMode}
              onChangeValue={updateValue}
            />
            <TemperatureSliderGroup
              selectedMode={mode}
              values={values}
              disabled={!isReachable}
              onSelectMode={chooseMode}
              onChangeValue={updateValue}
            />
            <TransitionSliderGroup
              selectedMode={mode}
              values={values}
              disabled={!isReachable}
              onSelectMode={chooseMode}
              onChangeValue={updateValue}
            />
          </div>
        </section>
        </main>
      </div>
    </div>
  );
}
