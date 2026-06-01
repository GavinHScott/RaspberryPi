package com.SmartDeviceManager.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.SmartDeviceManager.TestDevices;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.registry.DeviceRegistry;
import com.SmartDeviceManager.service.CommandParser;
import com.SmartDeviceManager.service.DeviceHealthService;
import com.SmartDeviceManager.service.SunsetService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DeviceControllerTest {

    private final CommandParser commandParser = mock(CommandParser.class);
    private final DeviceUdpClient udpClient = mock(DeviceUdpClient.class);
    private final DeviceRegistry registry = mock(DeviceRegistry.class);
    private final DeviceHealthService deviceHealthService = mock(DeviceHealthService.class);
    private final SunsetService sunsetService = mock(SunsetService.class);
    private final SmartDevice device = TestDevices.device("ref", "lamp", true);
    private final DeviceController controller = new DeviceController(
            commandParser, udpClient, registry, deviceHealthService, sunsetService);

    @BeforeEach
    void setUp() {
        device.setOnline(true);
        device.setLastSeen(Instant.parse("2026-06-01T12:00:00Z"));
        when(registry.getAll()).thenReturn(List.of(device));
        when(registry.getByName("lamp")).thenReturn(device);
    }

    @Test
    void devicesReturnsDeviceNames() {
        assertEquals(List.of("lamp"), controller.devices().getBody());
    }

    @Test
    void deviceDetailsReturnsDeviceMetadata() {
        Map<String, Object> info = controller.deviceDetails().getBody().getFirst();

        assertEquals("lamp", info.get("name"));
        assertEquals("ref", info.get("refName"));
        assertEquals("127.0.0.1", info.get("ip"));
        assertEquals(true, info.get("offlineDetection"));
        assertEquals(true, info.get("online"));
        assertEquals("2026-06-01T12:00:00Z", info.get("lastSeen"));
    }

    @Test
    void refreshDevicesUpdatesHealthBeforeReturningMetadata() {
        controller.refreshDevices();

        verify(deviceHealthService).updateDeviceHealth(device);
    }

    @Test
    void commandsReturnsSupportedCommandMetadata() {
        List<Map<String, Object>> commands = controller.commands().getBody();

        assertEquals(List.of("on", "off", "temp", "fade", "transition", "rgb", "sunrise"),
                commands.stream().map(command -> command.get("name")).toList());
    }

    @Test
    void deviceStatesReturnsParsedResultWhenDeviceResponds() throws Exception {
        when(udpClient.getState("ref")).thenReturn("{\"result\":{\"state\":true,\"dimming\":50}}");

        Map<String, Object> state = controller.deviceStates().getBody().getFirst();

        assertEquals("lamp", state.get("name"));
        assertEquals("ref", state.get("refName"));
        assertEquals(true, state.get("online"));
        assertEquals(Map.of("state", true, "dimming", 50.0), state.get("state"));
    }

    @Test
    void deviceStatesReturnsErrorWhenDeviceStateCannotBeRead() throws Exception {
        when(udpClient.getState("ref")).thenThrow(new Exception("offline"));

        Map<String, Object> state = controller.deviceStates().getBody().getFirst();

        assertEquals(false, state.get("online"));
        assertEquals("offline", state.get("error"));
    }

    @Test
    void dashboardInfoReturnsAppMetadata() {
        Map<String, Object> info = controller.dashboardInfo().getBody();

        assertTrue(info.containsKey("app"));
        assertTrue(info.containsKey("scheduledScripts"));
    }

    @Test
    void commandReturnsOkOrMappedErrors() throws Exception {
        assertEquals(HttpStatus.OK, controller.command(new CommandRequest("lamp", "on", null)).getStatusCode());

        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad")).when(commandParser).parseAndExecute("lamp off");
        assertEquals(HttpStatus.BAD_REQUEST, controller.command(new CommandRequest("lamp", "off", null)).getStatusCode());

        org.mockito.Mockito.doThrow(new Exception("boom")).when(commandParser).parseAndExecute("lamp fade 50");
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                controller.command(new CommandRequest("lamp", "fade", List.of("50"))).getStatusCode());
    }

    @Test
    void customCommandRejectsUnknownDevice() {
        when(registry.getByName("missing")).thenReturn(null);

        var response = controller.customCommand(new CustomCommandRequest("missing", 1, 1, null, null, null, false, false, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Unknown device: missing", response.getBody());
    }

    @Test
    void customCommandBuildsTemperaturePayloadAndRecordsManualCommand() throws Exception {
        var response = controller.customCommand(new CustomCommandRequest("lamp", 55, 50, null, null, null, false, false, null));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonObject params = sentParams();
        assertEquals(55, params.get("dimming").getAsInt());
        assertEquals(4350, params.get("temp").getAsInt());
        assertEquals(0, params.get("r").getAsInt());
        verify(sunsetService).recordManualCommand(device, "custom-command");
    }

    @Test
    void customCommandBuildsColourPayloadAndClampsValues() throws Exception {
        controller.customCommand(new CustomCommandRequest("lamp", null, null, -1, 300, 20, true, false, null));

        JsonObject params = sentParams();
        assertEquals(0, params.get("r").getAsInt());
        assertEquals(255, params.get("g").getAsInt());
        assertEquals(20, params.get("b").getAsInt());
    }

    @Test
    void customCommandBuildsTransitionPayload() throws Exception {
        controller.customCommand(new CustomCommandRequest("lamp", null, null, null, null, null, false, true, 100));

        JsonObject params = sentParams();
        assertEquals(6500, params.get("temp").getAsInt());
        assertEquals(100, params.get("dimming").getAsInt());
    }

    @Test
    void customCommandReturnsBadRequestForMissingRequiredValue() {
        var response = controller.customCommand(new CustomCommandRequest("lamp", null, null, null, null, null, true, false, null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("red is required", response.getBody());
    }

    @Test
    void customCommandReturnsServerErrorWhenUdpSendFails() throws Exception {
        org.mockito.Mockito.doThrow(new Exception("network")).when(udpClient).send(eq("ref"), anyString());

        var response = controller.customCommand(new CustomCommandRequest("lamp", null, 50, null, null, null, false, false, null));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("network", response.getBody());
    }

    private JsonObject sentParams() throws Exception {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(udpClient).send(eq("ref"), payload.capture());
        JsonObject root = JsonParser.parseString(payload.getValue()).getAsJsonObject();
        assertEquals("setPilot", root.get("method").getAsString());
        assertEquals(1, root.get("id").getAsInt());
        assertFalse(root.getAsJsonObject("params").entrySet().isEmpty());
        return root.getAsJsonObject("params");
    }
}
