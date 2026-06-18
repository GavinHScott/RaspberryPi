package com.SmartDeviceManager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SmartDeviceTest {

    @Test
    void exposesConstructorValuesAndMutableHealthState() throws Exception {
        InetAddress address = InetAddress.getByName("127.0.0.1");
        SmartDevice device = new SmartDevice("ref", "name", address, true);
        Instant lastSeen = Instant.parse("2026-06-01T12:00:00Z");

        assertEquals("ref", device.getRefName());
        assertEquals("name", device.getName());
        assertSame(address, device.getInetAddress());
        assertTrue(device.isOfflineDetection());
        assertFalse(device.isOnline());

        device.setOnline(true);
        device.setLastSeen(lastSeen);

        assertTrue(device.isOnline());
        assertEquals(lastSeen, device.getLastSeen());
    }
}
