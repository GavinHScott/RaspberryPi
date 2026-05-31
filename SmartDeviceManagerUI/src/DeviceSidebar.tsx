import { Button } from "@salt-ds/core";

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
        <Button
          className="refresh-devices"
          type="button"
          appearance="bordered"
          sentiment="neutral"
          disabled={refreshing}
          onClick={onRefreshDevices}
        >
          {refreshing ? "Refreshing" : "Refresh"}
        </Button>
      </div>
      <div className="device-list">
        {devices.map((device) => {
          const liveState = deviceStates.find((item) => item.name === device);
          const reachable = liveState?.online;

          return (
            <Button
              type="button"
              key={device}
              appearance="bordered"
              sentiment={device === selectedDevice ? "accented" : "neutral"}
              className={device === selectedDevice ? "device selected" : "device"}
              onClick={() => onSelectDevice(device)}
            >
              <span>{device}</span>
              <small className={reachable ? "online-text" : "offline-text"}>
                {reachable ? "Online" : "Offline"}
              </small>
            </Button>
          );
        })}
        {!loading && devices.length === 0 ? <p className="empty">No devices found</p> : null}
      </div>
    </aside>
  );
}
