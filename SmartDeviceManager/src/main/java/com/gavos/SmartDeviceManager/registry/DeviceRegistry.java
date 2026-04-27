package com.gavos.SmartDeviceManager.registry;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.gavos.SmartDeviceManager.model.SmartDevice;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import jakarta.annotation.PostConstruct;

@Component
public class DeviceRegistry {

    @Value("classpath:/com/gavos/SmartDeviceManager/AllDevices.json")
    private Resource devicesResource;

    private final List<SmartDevice> devices = new ArrayList<>();

    @PostConstruct
    private void init() throws IOException {
        try (var stream = devicesResource.getInputStream()) {
            String jsonText = new String(stream.readAllBytes());
            DevicesFile data;
            try {
                data = new Gson().fromJson(jsonText, DevicesFile.class);
            } catch (JsonSyntaxException e) {
                throw new IOException("AllDevices.json is not valid JSON: " + e.getMessage(), e);
            }

            if (data == null || data.devices == null) return;

            for (DeviceEntry entry : data.devices) {
                if (entry.refName != null && entry.name != null && entry.ip != null) {
                    InetAddress address = InetAddress.getByName(entry.ip);
                    SmartDevice device = new SmartDevice(entry.refName, entry.name, address);
                    devices.add(device);
                }
            }
        }
    }

    public List<SmartDevice> getAll() {
        return List.copyOf(devices);
    }

    public SmartDevice getByName(String name) {
        return devices.stream()
                .filter(d -> d.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public SmartDevice getByRefName(String refName) {
        return devices.stream()
                .filter(d -> d.getRefName().equals(refName))
                .findFirst().orElse(null);
    }

    private static class DevicesFile {
        @SerializedName("devices")
        List<DeviceEntry> devices;
    }

    private static class DeviceEntry {
        @SerializedName("refName") String refName;
        @SerializedName("name")    String name;
        @SerializedName("ip")      String ip;
    }
}
