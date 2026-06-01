package com.SmartDeviceManager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.TestDevices;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;

class DeviceHealthServiceTest {

    private final DeviceUdpClient udpClient = mock(DeviceUdpClient.class);
    private final NtfyNotificationService notifications = mock(NtfyNotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);
    private final DeviceHealthService service = new DeviceHealthService(udpClient, notifications, clock);

    @Test
    void startAndStopMonitoringSendNotificationsOnce() {
        SmartDevice device = TestDevices.device("ref", "lamp");

        service.startMonitoring(List.of(device));
        service.startMonitoring(List.of(device));
        service.stopMonitoring();
        service.stopMonitoring();

        verify(notifications).send("DeviceHealthService monitoring started",
                "DeviceHealthService started checking: ref (lamp).");
        verify(notifications).send("DeviceHealthService monitoring stopped",
                "DeviceHealthService stopped checking scheduled devices.");
    }

    @Test
    void startMonitoringDescribesEmptyDeviceList() {
        service.startMonitoring(List.of());

        verify(notifications).send("DeviceHealthService monitoring started",
                "DeviceHealthService started checking: none.");
    }

    @Test
    void updateDeviceHealthSetsOnlineAndLastSeen() {
        SmartDevice device = TestDevices.device("ref", "lamp");
        when(udpClient.ping("ref")).thenReturn(true);

        assertTrue(service.updateDeviceHealth(device));

        assertTrue(device.isOnline());
        assertEquals(Instant.parse("2026-06-01T12:00:00Z"), device.getLastSeen());
        verify(notifications, never()).send("DeviceHealthService state changed",
                "DeviceHealthService saw ref change to online.");
    }

    @Test
    void updateDeviceHealthNotifiesOnlyWhenStateChangesAfterInitialObservation() {
        SmartDevice device = TestDevices.device("ref", "lamp");
        when(udpClient.ping("ref")).thenReturn(true, false, false, true);

        service.updateDeviceHealth(device);
        assertFalse(service.updateDeviceHealth(device));
        assertFalse(service.updateDeviceHealth(device));
        assertTrue(service.updateDeviceHealth(device));

        verify(notifications).send("DeviceHealthService state changed",
                "DeviceHealthService saw ref change to offline.");
        verify(notifications).send("DeviceHealthService state changed",
                "DeviceHealthService saw ref change to online.");
    }
}
