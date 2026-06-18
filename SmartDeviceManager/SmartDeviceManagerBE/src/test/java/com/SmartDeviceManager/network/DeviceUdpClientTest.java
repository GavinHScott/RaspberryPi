package com.SmartDeviceManager.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.registry.DeviceRegistry;

class DeviceUdpClientTest {

    @Test
    void sendForResponseThrowsWhenDeviceIsUnknown() {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        when(registry.getByRefName("missing")).thenReturn(null);
        DeviceUdpClient client = new DeviceUdpClient(registry);

        assertThrows(Exception.class, () -> client.sendForResponse("missing", "{}"));
    }

    @Test
    void sendDelegatesToSendForResponseAndThrowsWhenDeviceIsUnknown() {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        when(registry.getByRefName("missing")).thenReturn(null);
        DeviceUdpClient client = new DeviceUdpClient(registry);

        assertThrows(Exception.class, () -> client.send("missing", "{}"));
    }

    @Test
    void getStateThrowsWhenDeviceIsUnknown() {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        when(registry.getByRefName("missing")).thenReturn(null);
        DeviceUdpClient client = new DeviceUdpClient(registry);

        assertThrows(Exception.class, () -> client.getState("missing"));
    }

    @Test
    void pingByRefNameReturnsFalseWhenDeviceIsUnknown() {
        DeviceRegistry registry = mock(DeviceRegistry.class);
        when(registry.getByRefName("missing")).thenReturn(null);
        DeviceUdpClient client = new DeviceUdpClient(registry);

        assertFalse(client.ping("missing"));
    }

    @Test
    void staticPingReturnsFalseWhenNoDeviceResponds() throws Exception {
        assertFalse(DeviceUdpClient.ping(InetAddress.getByName("127.0.0.1")));
    }
}
