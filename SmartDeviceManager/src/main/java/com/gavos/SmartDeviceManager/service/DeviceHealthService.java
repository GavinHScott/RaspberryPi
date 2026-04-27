package com.gavos.SmartDeviceManager.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.gavos.SmartDeviceManager.model.SmartDevice;
import com.gavos.SmartDeviceManager.network.DeviceUdpClient;

@Service
public class DeviceHealthService {

    private final DeviceUdpClient udpClient;
    private final Clock clock;

    public DeviceHealthService(DeviceUdpClient udpClient) {
        this(udpClient, Clock.systemDefaultZone());
    }

    DeviceHealthService(DeviceUdpClient udpClient, Clock clock) {
        this.udpClient = udpClient;
        this.clock = clock;
    }

    public boolean updateDeviceHealth(SmartDevice device) {
        return updateDeviceHealth(device, Instant.now(clock));
    }

    boolean updateDeviceHealth(SmartDevice device, Instant checkedAt) {
        boolean online = udpClient.ping(device.getRefName());
        device.setOnline(online);

        if (online) {
            device.setLastSeen(checkedAt);
        }

        return online;
    }
}
