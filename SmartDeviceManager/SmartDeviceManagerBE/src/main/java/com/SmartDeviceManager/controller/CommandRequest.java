package com.SmartDeviceManager.controller;

import java.util.Collections;
import java.util.List;

public record CommandRequest(String name, String command, List<String> params) {

    public CommandRequest {
        if (params == null) params = Collections.emptyList();
    }

    public String toCommandString() {
        if (params.isEmpty()) {
            return name + " " + command;
        }
        return name + " " + command + " " + String.join(" ", params);
    }
}
