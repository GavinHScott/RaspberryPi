package com.gavos.SmartDeviceManager.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gavos.SmartDeviceManager.registry.DeviceRegistry;
import com.gavos.SmartDeviceManager.service.CommandParser;

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
