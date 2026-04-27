package com.gavos.SmartDeviceManager.model;

import java.util.List;

public record DeviceCommand(String refName, String command, List<String> params) {}
