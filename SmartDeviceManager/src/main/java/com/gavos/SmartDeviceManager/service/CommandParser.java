package com.gavos.SmartDeviceManager.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.gavos.SmartDeviceManager.model.DeviceCommand;
import com.gavos.SmartDeviceManager.model.SmartDevice;
import com.gavos.SmartDeviceManager.network.DeviceUdpClient;
import com.gavos.SmartDeviceManager.payload.PayloadBuilder;
import com.gavos.SmartDeviceManager.registry.DeviceRegistry;

@Service
public class CommandParser {

    private final DeviceRegistry registry;
    private final PayloadBuilder payloadBuilder;
    private final DeviceUdpClient udpClient;
    private final SunriseService sunriseService;

    public CommandParser(DeviceRegistry registry, PayloadBuilder payloadBuilder,
                         DeviceUdpClient udpClient, SunriseService sunriseService) {
        this.registry = registry;
        this.payloadBuilder = payloadBuilder;
        this.udpClient = udpClient;
        this.sunriseService = sunriseService;
    }

    public void parseAndExecute(String input) throws Exception {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input command cannot be empty\n");
        }

        // Format: <name> <command> [values...]
        String[] parts = input.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid command format. Expected: <name> <command> [values]\n");
        }

        String name = parts[0];
        SmartDevice device = registry.getByName(name);
        if (device == null) {
            throw new IllegalArgumentException("Unknown device: " + name + "\n");
        }

        String command = parts[1].toLowerCase();
        List<String> params = List.of(parts).subList(2, parts.length);

        validateParams(command, params);

        if (command.equals("sunrise")) {
            int minutes = Integer.parseInt(params.get(0));
            sunriseService.start(device.getRefName(), minutes);
            return;
        }

        DeviceCommand cmd = new DeviceCommand(device.getRefName(), command, params);
        String json = payloadBuilder.build(cmd);
        udpClient.send(device.getRefName(), json);
    }

    private void validateParams(String command, List<String> params) {
        switch (command) {
            case "on", "off" -> {
                if (!params.isEmpty()) throw new IllegalArgumentException(command + " takes no values\n");
            }
            case "temp", "fade" -> {
                if (params.size() != 1) throw new IllegalArgumentException(command + " requires one integer value\n");
                parseInt(params.get(0), command);
            }
            case "rgb" -> {
                if (params.size() != 3) throw new IllegalArgumentException("rgb requires three integer values: r g b\n");
                parseInt(params.get(0), "r");
                parseInt(params.get(1), "g");
                parseInt(params.get(2), "b");
            }
            case "sunrise" -> {
                if (params.size() != 1) throw new IllegalArgumentException("sunrise requires one integer value (minutes)\n");
                int minutes = parseInt(params.get(0), "minutes");
                if (minutes < 1) throw new IllegalArgumentException("sunrise duration must be at least 1 minute\n");
            }
            default -> throw new IllegalArgumentException("Unknown command: " + command + "\n");
        }
    }

    private int parseInt(String value, String paramName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for " + paramName + ": " + value + "\n");
        }
    }
}
