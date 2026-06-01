package com.SmartDeviceManager.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.TestDevices;
import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

class SunsetServiceTest {

    private final DeviceRegistry registry = mock(DeviceRegistry.class);
    private final DeviceHealthService deviceHealthService = mock(DeviceHealthService.class);
    private final DeviceUdpClient udpClient = mock(DeviceUdpClient.class);
    private final PayloadBuilder payloadBuilder = mock(PayloadBuilder.class);
    private final NtfyNotificationService notifications = mock(NtfyNotificationService.class);

    @Test
    void outsideDetectionWindowDoesNotMonitorDevices() {
        SunsetService service = serviceAt("2026-06-01T19:00:00Z");

        service.runScheduledSunsetChecks();

        verify(registry, never()).getAll();
        verify(deviceHealthService, never()).startMonitoring(any());
    }

    @Test
    void opensMonitoringWindowAndSendsNightlyOffForInitiallyOnlineDevice() throws Exception {
        SmartDevice device = TestDevices.device("ref", "lamp", true);
        when(registry.getAll()).thenReturn(List.of(device));
        when(deviceHealthService.updateDeviceHealth(eq(device), any())).thenReturn(true);
        when(payloadBuilder.build(new DeviceCommand("ref", "off", List.of()))).thenReturn("off-payload");
        SunsetService service = serviceAt("2026-06-01T21:30:00Z");

        service.runScheduledSunsetChecks();

        verify(deviceHealthService).startMonitoring(List.of(device));
        verify(udpClient).send("ref", "off-payload");
        verify(notifications).send("Nightly off succeeded", "ref confirmed off.");
    }

    @Test
    void recordsManualCommandOnlyForOfflineDetectedDeviceDuringWindow() {
        SmartDevice monitored = TestDevices.device("ref", "lamp", true);
        SmartDevice ignored = TestDevices.device("other", "desk", false);
        SunsetService service = serviceAt("2026-06-01T21:30:00Z");

        service.recordManualCommand(monitored, "on");
        service.recordManualCommand(monitored, "on");
        service.recordManualCommand(ignored, "on");
        service.recordManualCommand(null, "on");

        verify(notifications).send(eq("Nightly off overridden"), contains("ref received manual command 'on'"));
    }

    @Test
    void manualOverrideSkipsNightlyOffCheck() throws Exception {
        SmartDevice device = TestDevices.device("ref", "lamp", true);
        when(registry.getAll()).thenReturn(List.of(device));
        SunsetService service = serviceAt("2026-06-01T21:30:00Z");
        service.recordManualCommand(device, "on");

        service.runScheduledSunsetChecks();

        verify(deviceHealthService, never()).updateDeviceHealth(eq(device), any());
        verify(udpClient, never()).send(any(), any());
    }

    @Test
    void sendsFailureNotificationWhenNightlyOffFails() throws Exception {
        SmartDevice device = TestDevices.device("ref", "lamp", true);
        when(registry.getAll()).thenReturn(List.of(device));
        when(deviceHealthService.updateDeviceHealth(eq(device), any())).thenReturn(true);
        when(payloadBuilder.build(new DeviceCommand("ref", "off", List.of()))).thenReturn("off-payload");
        org.mockito.Mockito.doThrow(new Exception("boom")).when(udpClient).send("ref", "off-payload");
        SunsetService service = serviceAt("2026-06-01T21:30:00Z");

        service.runScheduledSunsetChecks();

        verify(notifications).send("Nightly off failed", "Nightly off failed for ref: boom");
    }

    private SunsetService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
        return new SunsetService(registry, deviceHealthService, udpClient, payloadBuilder, notifications,
                clock, new DirectExecutorService());
    }

    private static class DirectExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
