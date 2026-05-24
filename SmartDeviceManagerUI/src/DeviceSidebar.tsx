type DeviceState = {
  name: string;
  online: boolean;
};

type DeviceSidebarProps = {
  devices: string[];
  deviceStates: DeviceState[];
  selectedDevice: string;
  loading: boolean;
  refreshing: boolean;
  onSelectDevice: (deviceName: string) => void;
  onRefreshDevices: () => void;
};

export function DeviceSidebar({
  devices,
  deviceStates,
  selectedDevice,
  loading,
  refreshing,
  onSelectDevice,
  onRefreshDevices,
}: DeviceSidebarProps) {
  return (
    <aside className="device-sidebar" aria-label="Available devices">
      <div className="sidebar-heading">
        <p className="eyebrow">Devices</p>
        <button className="refresh-devices" type="button" disabled={refreshing} onClick={onRefreshDevices}>
          {refreshing ? "Refreshing" : "Refresh"}
        </button>
      </div>
      <div className="device-list">
        {devices.map((device) => {
          const liveState = deviceStates.find((item) => item.name === device);
          const reachable = liveState?.online;

          return (
            <button
              type="button"
              key={device}
              className={device === selectedDevice ? "device selected" : "device"}
              onClick={() => onSelectDevice(device)}
            >
              <span>{device}</span>
              <small className={reachable ? "online-text" : "offline-text"}>
                {reachable ? "Online" : "Offline"}
              </small>
            </button>
          );
        })}
        {!loading && devices.length === 0 ? <p className="empty">No devices found</p> : null}
      </div>
    </aside>
  );
}
