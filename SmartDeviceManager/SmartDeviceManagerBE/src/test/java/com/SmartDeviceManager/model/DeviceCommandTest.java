package com.SmartDeviceManager.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class DeviceCommandTest {

    @Test
    void storesCommandParts() {
        DeviceCommand command = new DeviceCommand("ref", "fade", List.of("50"));

        assertEquals("ref", command.refName());
        assertEquals("fade", command.command());
        assertEquals(List.of("50"), command.params());
    }
}
