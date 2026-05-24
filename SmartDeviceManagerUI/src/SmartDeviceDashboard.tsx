import { useEffect, useState } from "react";

const API_PORT = 9090;
const apiBase = `${window.location.protocol}//${window.location.hostname}:${API_PORT}`;
const DEFAULT_DEVICE = "bedroomlight";

type StatusTone = "idle" | "busy" | "success" | "error";

type CommandParam = {
  name: string;
  type: "range" | "number";
  min?: number;
  max?: number;
  defaultValue?: number;
  unit?: string;
};

type DeviceCommand = {
  name: string;
  label: string;
  params: CommandParam[];
};

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

type ScheduledScript = {
  name: string;
  schedule: string;
  unit: string;
  script: string;
  description: string;
};

type DashboardInfo = {
  app: {
    name: string;
    apiPort: number;
    uiPort: number;
    staticIp: string;
  };
  scheduledScripts: ScheduledScript[];
};

type Status = {
  tone: StatusTone;
  text: string;
};

type CustomValues = {
  brightness: string;
  temperature: string;
  red: string;
  green: string;
  blue: string;
  colourMode: boolean;
  transitionMode: boolean;
  transition: string;
};

const colourFields: Array<["red" | "green" | "blue", number]> = [
  ["red", 255],
  ["green", 255],
  ["blue", 255],
];

const fallbackCommands: DeviceCommand[] = [
  { name: "on", label: "Turn on", params: [] },
  { name: "off", label: "Turn off", params: [] },
  {
    name: "temp",
    label: "Set white temperature",
    params: [{ name: "temperature", type: "range", min: 0, max: 100, defaultValue: 65, unit: "%" }],
  },
  {
    name: "fade",
    label: "Set brightness",
    params: [{ name: "brightness", type: "range", min: 0, max: 100, defaultValue: 50, unit: "%" }],
  },
  {
    name: "transition",
    label: "Transition",
    params: [{ name: "value", type: "range", min: 0, max: 100, defaultValue: 50, unit: "%" }],
  },
  {
    name: "rgb",
    label: "Set colour",
    params: [
      { name: "red", type: "range", min: 0, max: 255, defaultValue: 255 },
      { name: "green", type: "range", min: 0, max: 255, defaultValue: 160 },
      { name: "blue", type: "range", min: 0, max: 255, defaultValue: 60 },
    ],
  },
  {
    name: "sunrise",
    label: "Start sunrise",
    params: [{ name: "minutes", type: "number", min: 1, defaultValue: 60, unit: "min" }],
  },
];

function kelvinToPercent(value: unknown) {
  if (typeof value !== "number") return "";
  const percent = Math.round(((value - 2200) / (6500 - 2200)) * 100);
  return String(Math.max(0, Math.min(100, percent)));
}

function valueFromState(commandName: string, param: CommandParam, state?: Record<string, unknown>) {
  if (!state) return String(param.defaultValue ?? param.min ?? "");

  if (commandName === "temp") return kelvinToPercent(state.temp);
  if (commandName === "fade") return String(state.dimming ?? param.defaultValue ?? "");
  if (commandName === "transition") return String(state.dimming ?? param.defaultValue ?? "");
  if (commandName === "rgb") {
    const keyByParam: Record<string, string> = { red: "r", green: "g", blue: "b" };
    return String(state[keyByParam[param.name]] ?? param.defaultValue ?? "");
  }

  return String(param.defaultValue ?? param.min ?? "");
}

function valuesFromState(command: DeviceCommand | undefined, state?: Record<string, unknown>) {
  if (!command) return {};

  return Object.fromEntries(
    command.params.map((param) => [param.name, valueFromState(command.name, param, state)]),
  );
}

function commandFromState(commands: DeviceCommand[], state?: Record<string, unknown>) {
  if (!state) return commands[0]?.name ?? "on";
  if (state.state === false) return "off";
  if (typeof state.r === "number" || typeof state.g === "number" || typeof state.b === "number") return "rgb";
  if (typeof state.temp === "number") return "temp";
  if (typeof state.dimming === "number") return "fade";
  return "on";
}

function customValuesFromState(state?: Record<string, unknown>): CustomValues {
  return {
    brightness: String(state?.dimming ?? 50),
    temperature: kelvinToPercent(state?.temp) || "65",
    red: String(state?.r ?? 255),
    green: String(state?.g ?? 160),
    blue: String(state?.b ?? 60),
    colourMode: typeof state?.r === "number" || typeof state?.g === "number" || typeof state?.b === "number",
    transitionMode: false,
    transition: String(state?.dimming ?? 50),
  };
}

export function SmartDeviceDashboard() {
  const [devices, setDevices] = useState<string[]>([]);
  const [deviceDetails, setDeviceDetails] = useState<DeviceInfo[]>([]);
  const [deviceStates, setDeviceStates] = useState<DeviceState[]>([]);
  const [dashboardInfo, setDashboardInfo] = useState<DashboardInfo | null>(null);
  const [commands, setCommands] = useState<DeviceCommand[]>(fallbackCommands);
  const [selectedDevice, setSelectedDevice] = useState("");
  const [selectedCommand, setSelectedCommand] = useState("on");
  const [values, setValues] = useState<Record<string, string>>({});
  const [customValues, setCustomValues] = useState<CustomValues>(customValuesFromState());
  const [status, setStatus] = useState<Status>({ tone: "idle", text: "Ready" });
  const [loading, setLoading] = useState(true);

  const command = commands.find((item) => item.name === selectedCommand) ?? commands[0];
  const selectedState = deviceStates.find((item) => item.name === selectedDevice);
  const selectedDetails = deviceDetails.find((item) => item.name === selectedDevice);

  useEffect(() => {
    Promise.all([
      fetch(`${apiBase}/devices`).then((response) => response.json() as Promise<string[]>),
      fetch(`${apiBase}/commands`)
        .then((response) => (response.ok ? (response.json() as Promise<DeviceCommand[]>) : fallbackCommands))
        .catch(() => fallbackCommands),
      fetch(`${apiBase}/devices/details`)
        .then((response) => (response.ok ? (response.json() as Promise<DeviceInfo[]>) : []))
        .catch(() => []),
      fetch(`${apiBase}/device-states`)
        .then((response) => (response.ok ? (response.json() as Promise<DeviceState[]>) : []))
        .catch(() => []),
      fetch(`${apiBase}/dashboard-info`)
        .then((response) => (response.ok ? (response.json() as Promise<DashboardInfo>) : null))
        .catch(() => null),
    ])
      .then(([deviceList, commandList, details, states, info]) => {
        const availableCommands = commandList.length ? commandList : fallbackCommands;
        const firstDevice = deviceList.includes(DEFAULT_DEVICE) ? DEFAULT_DEVICE : deviceList[0] ?? "";
        const firstState = states.find((item) => item.name === firstDevice)?.state;
        const firstCommandName = commandFromState(availableCommands, firstState);
        const firstCommand = availableCommands.find((item) => item.name === firstCommandName) ?? availableCommands[0];

        setDevices(deviceList);
        setDeviceDetails(details);
        setDeviceStates(states);
        setDashboardInfo(info);
        setCommands(availableCommands);
        setSelectedDevice(firstDevice);
        setSelectedCommand(firstCommand.name);
        setValues(valuesFromState(firstCommand, firstState));
        setCustomValues(customValuesFromState(firstState));
        setStatus({ tone: "idle", text: deviceList.length ? "Ready" : "No devices returned by the manager" });
      })
      .catch((error: Error) => {
        setStatus({ tone: "error", text: `Could not reach SmartDeviceManager on ${apiBase}: ${error.message}` });
      })
      .finally(() => setLoading(false));
  }, []);

  function chooseDevice(deviceName: string) {
    const nextState = deviceStates.find((item) => item.name === deviceName)?.state;
    const nextCommandName = commandFromState(commands, nextState);
    const nextCommand = commands.find((item) => item.name === nextCommandName) ?? commands[0];

    setSelectedDevice(deviceName);
    setSelectedCommand(nextCommand.name);
    setValues(valuesFromState(nextCommand, nextState));
    setCustomValues(customValuesFromState(nextState));
  }

  function chooseCommand(commandName: string) {
    const nextCommand = commands.find((item) => item.name === commandName);
    if (!nextCommand) return;

    const nextValues = commandName === selectedCommand ? values : valuesFromState(nextCommand, selectedState?.state);
    setSelectedCommand(commandName);
    setValues(nextValues);
    sendCommand(nextCommand, nextValues);
  }

  function updateValue(name: string, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
  }

  function updateCustomValue(name: keyof CustomValues, value: string | boolean) {
    setCustomValues((current) => ({ ...current, [name]: value }));
  }

  function paramValues(commandToSend: DeviceCommand, sourceValues: Record<string, string>) {
    return (commandToSend.params ?? []).map((param) =>
      String(sourceValues[param.name] ?? param.defaultValue ?? param.min ?? ""),
    );
  }

  function sendCommand(commandToSend: DeviceCommand, sourceValues = values) {
    if (!selectedDevice || !commandToSend) {
      setStatus({ tone: "error", text: "Choose a device and command first" });
      return;
    }

    setStatus({ tone: "busy", text: "Sending command..." });
    fetch(`${apiBase}/command`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: selectedDevice,
        command: commandToSend.name,
        params: paramValues(commandToSend, sourceValues),
      }),
    })
      .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
      .then((result) => {
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `${commandToSend.label} sent to ${selectedDevice}` : result.text.trim(),
        });
      })
      .catch((error: Error) => setStatus({ tone: "error", text: error.message }));
  }

  function sendCustomValues() {
    if (!selectedDevice) {
      setStatus({ tone: "error", text: "Choose a device first" });
      return;
    }

    setStatus({ tone: "busy", text: "Sending custom values..." });
    fetch(`${apiBase}/custom-command`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: selectedDevice,
        brightness: Number(customValues.brightness),
        temperature: Number(customValues.temperature),
        red: Number(customValues.red),
        green: Number(customValues.green),
        blue: Number(customValues.blue),
        colourMode: customValues.colourMode,
        transitionMode: customValues.transitionMode,
        transition: Number(customValues.transition),
      }),
    })
      .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
      .then((result) => {
        setStatus({
          tone: result.ok ? "success" : "error",
          text: result.ok ? `Custom values sent to ${selectedDevice}` : result.text.trim(),
        });
      })
      .catch((error: Error) => setStatus({ tone: "error", text: error.message }));
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

      <div className="control-panel">
        <section className="device-list" aria-label="Devices">
          {devices.map((device) => (
            <button
              type="button"
              key={device}
              className={device === selectedDevice ? "device selected" : "device"}
              onClick={() => chooseDevice(device)}
            >
              {device}
            </button>
          ))}
          {!loading && devices.length === 0 ? <p className="empty">No devices found</p> : null}
        </section>

        <section className="command-grid" aria-label="Commands">
          {commands.map((item) => (
            <button
              type="button"
              key={item.name}
              className={item.name === selectedCommand ? "command selected" : "command"}
              onClick={() => chooseCommand(item.name)}
            >
              <span>{item.label}</span>
              <small>{item.name}</small>
            </button>
          ))}
        </section>

        <section className="params">
          <h2>{command ? command.label : "Command"}</h2>
          {(command.params ?? []).length === 0 ? (
            <p className="empty">No extra values needed.</p>
          ) : (
            command.params.map((param) => (
              <label className="field" key={param.name}>
                <span>
                  {param.name}
                  {param.unit ? ` (${param.unit})` : ""}
                </span>
                <div className="input-row">
                  <input
                    type={param.type === "number" ? "number" : "range"}
                    min={param.min}
                    max={param.max}
                    value={values[param.name] ?? param.defaultValue ?? ""}
                    onChange={(event) => updateValue(param.name, event.target.value)}
                  />
                  <input
                    className="number-entry"
                    type="number"
                    min={param.min}
                    max={param.max}
                    value={values[param.name] ?? param.defaultValue ?? ""}
                    onChange={(event) => updateValue(param.name, event.target.value)}
                  />
                </div>
              </label>
            ))
          )}
        </section>
      </div>

      <section className="custom-panel" aria-label="Custom values">
        <div className="custom-heading">
          <div>
            <h2>Custom values</h2>
            <p className="empty">Set multiple values, then send them together.</p>
          </div>
          <label className="toggle">
            <input
              type="checkbox"
              checked={customValues.colourMode}
              disabled={customValues.transitionMode}
              onChange={(event) => updateCustomValue("colourMode", event.target.checked)}
            />
            <span>RGB mode</span>
          </label>
          <label className="toggle">
            <input
              type="checkbox"
              checked={customValues.transitionMode}
              onChange={(event) => updateCustomValue("transitionMode", event.target.checked)}
            />
            <span>Transition mode</span>
          </label>
        </div>

        <div className="custom-grid">
          <label className="field">
            <span>brightness (%)</span>
            <div className="input-row">
              <input
                type="range"
                min={0}
                max={100}
                value={customValues.brightness}
                onChange={(event) => updateCustomValue("brightness", event.target.value)}
              />
              <input
                className="number-entry"
                type="number"
                min={0}
                max={100}
                value={customValues.brightness}
                onChange={(event) => updateCustomValue("brightness", event.target.value)}
              />
            </div>
          </label>

          <label className="field">
            <span>temperature (%)</span>
            <div className="input-row">
              <input
                type="range"
                min={0}
                max={100}
                value={customValues.temperature}
                disabled={customValues.colourMode || customValues.transitionMode}
                onChange={(event) => updateCustomValue("temperature", event.target.value)}
              />
              <input
                className="number-entry"
                type="number"
                min={0}
                max={100}
                value={customValues.temperature}
                disabled={customValues.colourMode || customValues.transitionMode}
                onChange={(event) => updateCustomValue("temperature", event.target.value)}
              />
            </div>
          </label>

          <label className="field">
            <span>transition (%)</span>
            <div className="input-row">
              <input
                type="range"
                min={0}
                max={100}
                value={customValues.transition}
                disabled={!customValues.transitionMode}
                onChange={(event) => updateCustomValue("transition", event.target.value)}
              />
              <input
                className="number-entry"
                type="number"
                min={0}
                max={100}
                value={customValues.transition}
                disabled={!customValues.transitionMode}
                onChange={(event) => updateCustomValue("transition", event.target.value)}
              />
            </div>
          </label>

          {colourFields.map(([name, max]) => (
            <label className="field" key={name}>
              <span>{name}</span>
              <div className="input-row">
                <input
                  type="range"
                  min={0}
                  max={max}
                  value={customValues[name]}
                  disabled={!customValues.colourMode || customValues.transitionMode}
                  onChange={(event) => updateCustomValue(name, event.target.value)}
                />
                <input
                  className="number-entry"
                  type="number"
                  min={0}
                  max={max}
                  value={customValues[name]}
                  disabled={!customValues.colourMode || customValues.transitionMode}
                  onChange={(event) => updateCustomValue(name, event.target.value)}
                />
              </div>
            </label>
          ))}
        </div>

        <button className="custom-send" type="button" onClick={sendCustomValues}>
          Send custom values
        </button>
      </section>

      <section className="info-section" aria-label="Device and schedule information">
        <article>
          <h2>Device info</h2>
          <div className="info-grid">
            {deviceDetails.map((device) => {
              const liveState = deviceStates.find((item) => item.name === device.name);
              return (
                <div className="info-item" key={device.refName}>
                  <strong>{device.name}</strong>
                  <span>{device.refName}</span>
                  <span>{device.ip}</span>
                  <span>{liveState?.online ? "Online" : "Offline or unavailable"}</span>
                  {liveState?.state ? <code>{JSON.stringify(liveState.state)}</code> : null}
                  {liveState?.error ? <span className="error-text">{liveState.error}</span> : null}
                </div>
              );
            })}
          </div>
        </article>

        <article>
          <h2>Scheduled scripts</h2>
          <div className="info-grid">
            {(dashboardInfo?.scheduledScripts ?? []).map((script) => (
              <div className="info-item" key={script.name}>
                <strong>{script.name}</strong>
                <span>{script.schedule}</span>
                <span>{script.unit}</span>
                <code>{script.script}</code>
                <span>{script.description}</span>
              </div>
            ))}
          </div>
        </article>

        {selectedDetails ? (
          <article>
            <h2>Selected device</h2>
            <div className="selected-summary">
              <span>{selectedDetails.name}</span>
              <span>{selectedDetails.ip}</span>
              <span>{selectedState?.online ? "Live state loaded" : "State unavailable"}</span>
            </div>
          </article>
        ) : null}
      </section>
    </div>
  );
}
