package com.SmartDeviceManager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CustomCommandRequestTest {

    @Test
    void storesAllProvidedValues() {
        CustomCommandRequest request = new CustomCommandRequest(
                "light", 50, 65, 1, 2, 3, true, false, 80);

        assertEquals("light", request.name());
        assertEquals(50, request.brightness());
        assertEquals(65, request.temperature());
        assertEquals(1, request.red());
        assertEquals(2, request.green());
        assertEquals(3, request.blue());
        assertEquals(true, request.colourMode());
        assertEquals(false, request.transitionMode());
        assertEquals(80, request.transition());
    }
}
