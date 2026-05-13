(function () {
  const API_PORT = 9090;
  const apiBase = `${window.location.protocol}//${window.location.hostname}:${API_PORT}`;
  const fallbackCommands = [
    { name: "on", label: "Turn on", params: [] },
    { name: "off", label: "Turn off", params: [] },
    { name: "temp", label: "Set white temperature", params: [{ name: "temperature", type: "range", min: 0, max: 100, defaultValue: 65, unit: "%" }] },
    { name: "fade", label: "Set brightness", params: [{ name: "brightness", type: "range", min: 0, max: 100, defaultValue: 50, unit: "%" }] },
    { name: "transition", label: "Transition", params: [{ name: "value", type: "range", min: 0, max: 100, defaultValue: 50, unit: "%" }] },
    {
      name: "rgb",
      label: "Set colour",
      params: [
        { name: "red", type: "range", min: 0, max: 255, defaultValue: 255 },
        { name: "green", type: "range", min: 0, max: 255, defaultValue: 160 },
        { name: "blue", type: "range", min: 0, max: 255, defaultValue: 60 },
      ],
    },
    { name: "sunrise", label: "Start sunrise", params: [{ name: "minutes", type: "number", min: 1, defaultValue: 60, unit: "min" }] },
  ];

  const e = React.createElement;

  function initialValues(command) {
    return Object.fromEntries((command.params || []).map((param) => [param.name, String(param.defaultValue ?? param.min ?? "")]));
  }

  function App() {
    const [devices, setDevices] = React.useState([]);
    const [commands, setCommands] = React.useState(fallbackCommands);
    const [selectedDevice, setSelectedDevice] = React.useState("");
    const [selectedCommand, setSelectedCommand] = React.useState("on");
    const [values, setValues] = React.useState({});
    const [status, setStatus] = React.useState({ tone: "idle", text: "Ready" });
    const [loading, setLoading] = React.useState(true);

    const command = commands.find((item) => item.name === selectedCommand) || commands[0];

    React.useEffect(() => {
      Promise.all([
        fetch(`${apiBase}/devices`).then((response) => response.json()),
        fetch(`${apiBase}/commands`).then((response) => response.ok ? response.json() : fallbackCommands).catch(() => fallbackCommands),
      ])
        .then(([deviceList, commandList]) => {
          setDevices(deviceList);
          setCommands(commandList.length ? commandList : fallbackCommands);
          setSelectedDevice(deviceList[0] || "");
          setSelectedCommand((commandList[0] || fallbackCommands[0]).name);
          setValues(initialValues(commandList[0] || fallbackCommands[0]));
          setStatus({ tone: "idle", text: deviceList.length ? "Ready" : "No devices returned by the manager" });
        })
        .catch((error) => {
          setStatus({ tone: "error", text: `Could not reach SmartDeviceManager on ${apiBase}: ${error.message}` });
        })
        .finally(() => setLoading(false));
    }, []);

    function chooseCommand(commandName) {
      const next = commands.find((item) => item.name === commandName);
      setSelectedCommand(commandName);
      setValues(initialValues(next));
    }

    function updateValue(name, value) {
      setValues((current) => ({ ...current, [name]: value }));
    }

    function paramValues() {
      return (command.params || []).map((param) => String(values[param.name] ?? param.defaultValue ?? param.min ?? ""));
    }

    function submit(event) {
      event.preventDefault();
      if (!selectedDevice || !command) {
        setStatus({ tone: "error", text: "Choose a device and command first" });
        return;
      }

      setStatus({ tone: "busy", text: "Sending command..." });
      fetch(`${apiBase}/command`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: selectedDevice, command: command.name, params: paramValues() }),
      })
        .then((response) => response.text().then((text) => ({ ok: response.ok, text })))
        .then((result) => {
          setStatus({
            tone: result.ok ? "success" : "error",
            text: result.ok ? `${command.label} sent to ${selectedDevice}` : result.text.trim(),
          });
        })
        .catch((error) => setStatus({ tone: "error", text: error.message }));
    }

    return e(
      "div",
      { className: "app-shell" },
      e("header", { className: "topbar" },
        e("div", null,
          e("p", { className: "eyebrow" }, "Port 9091"),
          e("h1", null, "Smart Device Manager")
        ),
        e("div", { className: `status ${status.tone}` }, status.text)
      ),
      e("form", { className: "control-panel", onSubmit: submit },
        e("section", { className: "device-list", "aria-label": "Devices" },
          devices.map((device) =>
            e("button", {
              type: "button",
              key: device,
              className: device === selectedDevice ? "device selected" : "device",
              onClick: () => setSelectedDevice(device),
            }, device)
          ),
          !loading && devices.length === 0 ? e("p", { className: "empty" }, "No devices found") : null
        ),
        e("section", { className: "command-grid", "aria-label": "Commands" },
          commands.map((item) =>
            e("button", {
              type: "button",
              key: item.name,
              className: item.name === selectedCommand ? "command selected" : "command",
              onClick: () => chooseCommand(item.name),
            },
              e("span", null, item.label),
              e("small", null, item.name)
            )
          )
        ),
        e("section", { className: "params" },
          e("h2", null, command ? command.label : "Command"),
          (command.params || []).length === 0
            ? e("p", { className: "empty" }, "No extra values needed.")
            : command.params.map((param) =>
                e("label", { className: "field", key: param.name },
                  e("span", null, `${param.name}${param.unit ? ` (${param.unit})` : ""}`),
                  e("div", { className: "input-row" },
                    e("input", {
                      type: param.type === "number" ? "number" : "range",
                      min: param.min,
                      max: param.max,
                      value: values[param.name] ?? param.defaultValue ?? "",
                      onChange: (event) => updateValue(param.name, event.target.value),
                    }),
                    e("strong", null, values[param.name] ?? param.defaultValue ?? "")
                  )
                )
              ),
          e("button", { className: "send", type: "submit" }, "Send command")
        )
      )
    );
  }

  if (!window.React || !window.ReactDOM) {
    document.getElementById("root").innerHTML = "<section class=\"boot-message\"><h1>Smart Device Manager</h1><p>React could not be loaded. Check internet access for the React runtime CDN.</p></section>";
    return;
  }

  ReactDOM.createRoot(document.getElementById("root")).render(e(App));
})();
