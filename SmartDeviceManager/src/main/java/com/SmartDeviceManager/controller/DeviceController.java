package com.SmartDeviceManager.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.SmartDeviceManager.registry.DeviceRegistry;
import com.SmartDeviceManager.service.CommandParser;

@CrossOrigin(origins = "*")
@RestController
public class DeviceController {

    private final CommandParser commandParser;
    private final DeviceRegistry registry;

    public DeviceController(CommandParser commandParser, DeviceRegistry registry) {
        this.commandParser = commandParser;
        this.registry = registry;
    }

    @GetMapping("/devices")
    public ResponseEntity<List<String>> devices() {
        List<String> names = registry.getAll().stream()
                .map(d -> d.getName())
                .toList();
        return ResponseEntity.ok(names);
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
}
