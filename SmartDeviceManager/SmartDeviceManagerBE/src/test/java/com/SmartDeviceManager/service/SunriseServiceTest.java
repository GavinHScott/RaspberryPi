package com.SmartDeviceManager.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.TestDevices;
import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

class SunriseServiceTest {

    private final DeviceUdpClient udpClient = mock(DeviceUdpClient.class);
    private final PayloadBuilder payloadBuilder = mock(PayloadBuilder.class);
    private final DeviceRegistry registry = mock(DeviceRegistry.class);
    private final NtfyNotificationService notifications = mock(NtfyNotificationService.class);
    private final SunriseService service = new SunriseService(udpClient, payloadBuilder, registry, notifications);

    @AfterEach
    void shutdown() {
        service.shutdown();
    }

    @Test
    void startNotifiesWhenDeviceIsMissing() throws Exception {
        when(registry.getByRefName("missing")).thenReturn(null);

        service.start("missing", 1);

        verify(notifications).send("Sunrise start failed", "Bulb not found: missing");
        verify(udpClient, never()).send(any(), any());
    }

    @Test
    void startSchedulesFirstSunriseStepWhenDeviceRespondsToPing() throws Exception {
        SmartDevice device = TestDevices.device("ref", "lamp");
        when(registry.getByRefName("ref")).thenReturn(device);
        when(udpClient.ping("ref")).thenReturn(true);
        when(payloadBuilder.build(new DeviceCommand("ref", "transition", List.of("1")))).thenReturn("payload");

        service.start("ref", 1);

        verify(notifications).send("Sunrise started", "Sunrise found and connected to ref. Running for 1 minutes.");
        verify(udpClient, timeout(1000)).send("ref", "payload");
    }

    @Test
    void cancelIsSafeWhenNothingIsRunning() {
        service.cancel("ref");

        verify(notifications, never()).send(eq("Sunrise finished"), contains("ref"));
    }
}
