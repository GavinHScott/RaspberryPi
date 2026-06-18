package com.SmartDeviceManager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CommandRequestTest {

    @Test
    void nullParamsBecomeEmptyList() {
        CommandRequest request = new CommandRequest("light", "on", null);

        assertTrue(request.params().isEmpty());
        assertEquals("light on", request.toCommandString());
    }

    @Test
    void commandStringIncludesParamsWhenPresent() {
        CommandRequest request = new CommandRequest("light", "rgb", List.of("1", "2", "3"));

        assertEquals("light rgb 1 2 3", request.toCommandString());
    }
}
