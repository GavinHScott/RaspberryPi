package com.SmartDeviceManager.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.SmartDeviceManager.model.DeviceCommand;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class PayloadBuilderTest {

    private final PayloadBuilder builder = new PayloadBuilder();

    @Test
    void buildsOnAndOffPayloads() {
        assertTrue(params(builder.build(new DeviceCommand("ref", "on", List.of()))).get("state").getAsBoolean());
        assertFalse(params(builder.build(new DeviceCommand("ref", "off", List.of()))).get("state").getAsBoolean());
    }

    @Test
    void buildsTemperaturePayloadAndClearsColourChannels() {
        JsonObject params = params(builder.build(new DeviceCommand("ref", "temp", List.of("50"))));

        assertTrue(params.get("state").getAsBoolean());
        assertEquals(4350, params.get("temp").getAsInt());
        assertEquals(0, params.get("r").getAsInt());
        assertEquals(0, params.get("g").getAsInt());
        assertEquals(0, params.get("b").getAsInt());
    }

    @Test
    void clampsTemperaturePercentForTempFade() {
        assertEquals(2200, params(builder.buildTempFade(10, -20)).get("temp").getAsInt());
        assertEquals(6500, params(builder.buildTempFade(10, 200)).get("temp").getAsInt());
    }

    @Test
    void buildsFadeAndRgbPayloads() {
        JsonObject fade = params(builder.build(new DeviceCommand("ref", "fade", List.of("70"))));
        JsonObject rgb = params(builder.build(new DeviceCommand("ref", "rgb", List.of("1", "2", "3"))));

        assertEquals(70, fade.get("dimming").getAsInt());
        assertEquals(1, rgb.get("r").getAsInt());
        assertEquals(2, rgb.get("g").getAsInt());
        assertEquals(3, rgb.get("b").getAsInt());
    }

    @Test
    void buildsTransitionPayloadBelowAndAboveHandover() {
        JsonObject low = params(builder.build(new DeviceCommand("ref", "transition", List.of("0"))));
        JsonObject high = params(builder.build(new DeviceCommand("ref", "transition", List.of("100"))));

        assertEquals(1, low.get("w").getAsInt());
        assertEquals(1, low.get("dimming").getAsInt());
        assertEquals(6500, high.get("temp").getAsInt());
        assertEquals(100, high.get("dimming").getAsInt());
    }

    @Test
    void transitionClampsOutOfRangeValues() {
        JsonObject low = params(builder.build(new DeviceCommand("ref", "transition", List.of("-1"))));
        JsonObject high = params(builder.build(new DeviceCommand("ref", "transition", List.of("101"))));

        assertEquals(1, low.get("w").getAsInt());
        assertEquals(6500, high.get("temp").getAsInt());
    }

    @Test
    void rejectsUnsupportedCommand() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> builder.build(new DeviceCommand("ref", "bogus", List.of())));

        assertEquals("Unsupported command: bogus", exception.getMessage());
    }

    @Test
    void sunriseStepUsesTempFadePayload() {
        assertEquals(builder.buildTempFade(20, 30), builder.buildSunriseStep("ref", 20, 30));
    }

    private JsonObject params(String json) {
        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("setPilot", payload.get("method").getAsString());
        assertEquals(1, payload.get("id").getAsInt());
        return payload.getAsJsonObject("params");
    }
}
