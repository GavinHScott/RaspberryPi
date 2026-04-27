package com.gavos.SmartDeviceManager.payload;

import org.springframework.stereotype.Service;

import com.gavos.SmartDeviceManager.model.DeviceCommand;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Service
public class PayloadBuilder {

    private final Gson gson = new Gson();

    public String build(DeviceCommand cmd) {
        JsonObject params = new JsonObject();

        switch (cmd.command()) {
            case "on"  -> params.addProperty("state", true);
            case "off" -> params.addProperty("state", false);
            case "temp" -> {
                int input = Integer.parseInt(cmd.params().get(0));
                int temp = toKelvin(input);
                params.addProperty("state", true);
                params.addProperty("temp", temp);
                // Explicitly clear RGB to exit colour mode on the bulb
                params.addProperty("r", 0);
                params.addProperty("g", 0);
                params.addProperty("b", 0);
            }
            case "fade" -> {
                int dimming = Integer.parseInt(cmd.params().get(0));
                params.addProperty("state", true);
                params.addProperty("dimming", dimming);
            }
            case "rgb" -> {
                params.addProperty("state", true);
                params.addProperty("r", Integer.parseInt(cmd.params().get(0)));
                params.addProperty("g", Integer.parseInt(cmd.params().get(1)));
                params.addProperty("b", Integer.parseInt(cmd.params().get(2)));
            }
            default -> throw new IllegalArgumentException("Unsupported command: " + cmd.command());
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("method", "setPilot");
        payload.add("params", params);
        payload.addProperty("id", 1);
        return gson.toJson(payload);
    }

    public String buildSunriseStep(String refName, int fadeValue, int tempValue) {
        return buildTempFade(fadeValue, tempValue);
    }

    public String buildTempFade(int fadeValue, int tempValue) {
        JsonObject params = new JsonObject();
        params.addProperty("state", true);
        params.addProperty("dimming", fadeValue);
        params.addProperty("temp", toKelvin(tempValue));
        params.addProperty("r", 0);
        params.addProperty("g", 0);
        params.addProperty("b", 0);

        JsonObject payload = new JsonObject();
        payload.addProperty("method", "setPilot");
        payload.add("params", params);
        payload.addProperty("id", 1);
        return gson.toJson(payload);
    }

    private static final int TEMP_MIN = 2200;
    private static final int TEMP_MAX = 6500;

    private int toKelvin(int value) {
        int clamped = Math.max(0, Math.min(100, value));
        return TEMP_MIN + Math.round(clamped / 100f * (TEMP_MAX - TEMP_MIN));
    }
}
