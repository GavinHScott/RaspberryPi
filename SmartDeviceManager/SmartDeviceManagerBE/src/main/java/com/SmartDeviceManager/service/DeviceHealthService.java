package com.SmartDeviceManager.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;

@Service
public class DeviceHealthService {

    private final DeviceUdpClient udpClient;
    private final NtfyNotificationService notifications;
    private final Clock clock;
    private final Map<String, Boolean> lastNotifiedState = new ConcurrentHashMap<>();
    private volatile boolean monitoringActive;

    @Autowired
    public DeviceHealthService(DeviceUdpClient udpClient, NtfyNotificationService notifications) {
        this(udpClient, notifications, Clock.systemDefaultZone());
    }

    DeviceHealthService(DeviceUdpClient udpClient, NtfyNotificationService notifications, Clock clock) {
        this.udpClient = udpClient;
        this.notifications = notifications;
        this.clock = clock;
    }

    public void startMonitoring(Collection<SmartDevice> devices) {
        if (monitoringActive) {
            return;
        }

        monitoringActive = true;
        lastNotifiedState.clear();

        String deviceNames = devices.stream()
                .map(device -> device.getRefName() + " (" + device.getName() + ")")
                .collect(Collectors.joining(", "));
        if (deviceNames.isBlank()) {
            deviceNames = "none";
        }

        notifications.send("DeviceHealthService monitoring started",
                "DeviceHealthService started checking: " + deviceNames + ".");
    }

    public void stopMonitoring() {
        if (!monitoringActive) {
            return;
        }

        monitoringActive = false;
        lastNotifiedState.clear();
        notifications.send("DeviceHealthService monitoring stopped",
                "DeviceHealthService stopped checking scheduled devices.");
    }

    public boolean updateDeviceHealth(SmartDevice device) {
        return updateDeviceHealth(device, Instant.now(clock));
    }

    boolean updateDeviceHealth(SmartDevice device, Instant checkedAt) {
        boolean online = udpClient.ping(device.getRefName());
        device.setOnline(online);
        notifyIfStateChanged(device, online);

        if (online) {
            device.setLastSeen(checkedAt);
        }

        return online;
    }

    private void notifyIfStateChanged(SmartDevice device, boolean online) {
        Boolean previous = lastNotifiedState.put(device.getRefName(), online);
        if (previous == null || previous == online) {
            return;
        }

        String state = online ? "online" : "offline";
        notifications.send("DeviceHealthService state changed",
                "DeviceHealthService saw " + device.getRefName() + " change to " + state + ".");
    }
}
