package com.SmartDeviceManager.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.registry.DeviceRegistry;
import com.SmartDeviceManager.service.CommandParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@CrossOrigin(origins = "*")
@RestController
public class DeviceController {

    private static final Gson gson = new Gson();

    private final CommandParser commandParser;
    private final DeviceUdpClient udpClient;
    private final DeviceRegistry registry;

    public DeviceController(CommandParser commandParser, DeviceUdpClient udpClient, DeviceRegistry registry) {
        this.commandParser = commandParser;
        this.udpClient = udpClient;
        this.registry = registry;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<String>> devices() {
        List<String> names = registry.getAll().stream()
                .map(d -> d.getName())
                .toList();
        return ResponseEntity.ok(names);
    }

    @GetMapping("/devices/details")
    public ResponseEntity<List<Map<String, Object>>> deviceDetails() {
        return ResponseEntity.ok(registry.getAll().stream()
                .map(this::deviceInfo)
                .toList());
    }

    @GetMapping("/commands")
    public ResponseEntity<List<Map<String, Object>>> commands() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "name", "on",
                        "label", "Turn on",
                        "params", List.of()),
                Map.of(
                        "name", "off",
                        "label", "Turn off",
                        "params", List.of()),
                Map.of(
                        "name", "temp",
                        "label", "Set white temperature",
                        "params", List.of(Map.of(
                                "name", "temperature",
                                "type", "range",
                                "min", 0,
                                "max", 100,
                                "defaultValue", 65,
                                "unit", "%"))),
                Map.of(
                        "name", "fade",
                        "label", "Set brightness",
                        "params", List.of(Map.of(
                                "name", "brightness",
                                "type", "range",
                                "min", 0,
                                "max", 100,
                                "defaultValue", 50,
                                "unit", "%"))),
                Map.of(
                        "name", "transition",
                        "label", "Transition",
                        "params", List.of(Map.of(
                                "name", "value",
                                "type", "range",
                                "min", 0,
                                "max", 100,
                                "defaultValue", 50,
                                "unit", "%"))),
                Map.of(
                        "name", "rgb",
                        "label", "Set colour",
                        "params", List.of(
                                Map.of("name", "red", "type", "range", "min", 0, "max", 255, "defaultValue", 255),
                                Map.of("name", "green", "type", "range", "min", 0, "max", 255, "defaultValue", 160),
                                Map.of("name", "blue", "type", "range", "min", 0, "max", 255, "defaultValue", 60))),
                Map.of(
                        "name", "sunrise",
                        "label", "Start sunrise",
                        "params", List.of(Map.of(
                                "name", "minutes",
                                "type", "number",
                                "min", 1,
                                "defaultValue", 60,
                                "unit", "min")))));
    }

    @GetMapping("/device-states")
    public ResponseEntity<List<Map<String, Object>>> deviceStates() {
        List<Map<String, Object>> states = registry.getAll().stream()
                .map(this::stateForDevice)
                .toList();
        return ResponseEntity.ok(states);
    }

    @GetMapping("/dashboard-info")
    public ResponseEntity<Map<String, Object>> dashboardInfo() {
        return ResponseEntity.ok(Map.of(
                "app", Map.of(
                        "name", "SmartDeviceManager",
                        "apiPort", 9090,
                        "uiPort", 9091,
                        "staticIp", "192.168.4.56"),
                "scheduledScripts", List.of(
                        Map.of(
                                "name", "Boot update check",
                                "schedule", "On boot before SmartDeviceManager",
                                "unit", "smartdevicemanager-CheckForUpdates.service",
                                "script", "/home/gavinsco/scripts/prepare-smartdevicemanager.sh",
                                "description", "Pulls latest main and rebuilds the backend jar when the commit changes."),
                        Map.of(
                                "name", "Morning alarm",
                                "schedule", "Daily at 06:00 inside Spring",
                                "unit", "ScheduledTasks.java",
                                "script", "bedroomlight sunrise 60",
                                "description", "Starts the bedroom light sunrise sequence."),
                        Map.of(
                                "name", "Midnight reboot/update cycle",
                                "schedule", "Daily at 00:01 local time",
                                "unit", "smartdevicemanager-midnight-reboot.timer",
                                "script", "smartdevicemanager-midnight-reboot.service",
                                "description", "Stops the app/UI, reboots, then boot services update and restart everything."))));
    }

    @PostMapping("/command")
    public ResponseEntity<String> command(@RequestBody CommandRequest request) {
        try {
            commandParser.parseAndExecute(request.toCommandString());
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/custom-command")
    public ResponseEntity<String> customCommand(@RequestBody CustomCommandRequest request) {
        SmartDevice device = registry.getByName(request.name());
        if (device == null) {
            return ResponseEntity.badRequest().body("Unknown device: " + request.name());
        }

        try {
            udpClient.send(device.getRefName(), buildCustomPayload(request));
            return ResponseEntity.ok("OK");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    private Map<String, Object> deviceInfo(SmartDevice device) {
        return Map.of(
                "name", device.getName(),
                "refName", device.getRefName(),
                "ip", device.getInetAddress().getHostAddress(),
                "offlineDetection", device.isOfflineDetection(),
                "online", device.isOnline(),
                "lastSeen", device.getLastSeen() == null ? "" : device.getLastSeen().toString());
    }

    private Map<String, Object> stateForDevice(SmartDevice device) {
        try {
            String response = udpClient.getState(device.getRefName());
            JsonObject parsed = JsonParser.parseString(response).getAsJsonObject();
            Object result = parsed.has("result")
                    ? gson.fromJson(parsed.get("result"), Object.class)
                    : gson.fromJson(parsed, Object.class);
            return Map.of(
                    "name", device.getName(),
                    "refName", device.getRefName(),
                    "online", true,
                    "state", result);
        } catch (Exception e) {
            return Map.of(
                    "name", device.getName(),
                    "refName", device.getRefName(),
                    "online", false,
                    "error", e.getMessage() == null ? "Unable to read device state" : e.getMessage());
        }
    }

    private String buildCustomPayload(CustomCommandRequest request) {
        JsonObject params = new JsonObject();
        params.addProperty("state", true);

        if (request.brightness() != null) {
            params.addProperty("dimming", clamp(request.brightness(), 0, 100, "brightness"));
        }

        boolean transitionMode = Boolean.TRUE.equals(request.transitionMode());
        boolean colourMode = Boolean.TRUE.equals(request.colourMode());

        if (transitionMode) {
            JsonObject transitionParams = buildTransitionParams(clamp(request.transition(), 0, 100, "transition"));
            if (request.brightness() != null) {
                transitionParams.addProperty("dimming", clamp(request.brightness(), 0, 100, "brightness"));
            }
            params = transitionParams;
        } else if (colourMode) {
            params.addProperty("r", clamp(request.red(), 0, 255, "red"));
            params.addProperty("g", clamp(request.green(), 0, 255, "green"));
            params.addProperty("b", clamp(request.blue(), 0, 255, "blue"));
        } else {
            params.addProperty("temp", toKelvin(clamp(request.temperature(), 0, 100, "temperature")));
            params.addProperty("r", 0);
            params.addProperty("g", 0);
            params.addProperty("b", 0);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("method", "setPilot");
        payload.add("params", params);
        payload.addProperty("id", 1);
        return gson.toJson(payload);
    }

    private int clamp(Integer value, int min, int max, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return Math.max(min, Math.min(max, value));
    }

    private int toKelvin(int value) {
        return 2200 + Math.round(value / 100f * (6500 - 2200));
    }

    private JsonObject buildTransitionParams(int value) {
        int clamped = Math.max(0, Math.min(100, value));
        JsonObject params = new JsonObject();
        params.addProperty("state", true);

        if (clamped <= 10) {
            float ratio = clamped / 10f;
            params.addProperty("r", 0);
            params.addProperty("g", 0);
            params.addProperty("b", 0);
            params.addProperty("c", 0);
            params.addProperty("w", interpolate(1, 10, ratio));
            params.addProperty("dimming", interpolate(1, 100, ratio));
            return params;
        }

        float ratio = (clamped - 10) / 90f;
        params.addProperty("temp", interpolate(2800, 6500, ratio));
        params.addProperty("dimming", interpolate(10, 100, ratio));
        return params;
    }

    private int interpolate(int start, int end, float ratio) {
        return start + Math.round(ratio * (end - start));
    }
}
