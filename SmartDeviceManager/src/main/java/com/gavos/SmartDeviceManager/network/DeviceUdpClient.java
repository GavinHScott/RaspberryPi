package com.gavos.SmartDeviceManager.network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.springframework.stereotype.Service;

import com.gavos.SmartDeviceManager.model.SmartDevice;
import com.gavos.SmartDeviceManager.registry.DeviceRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

@Service
public class DeviceUdpClient {

    private static final int WIZ_PORT = 38899;
    private static final int SEND_TIMEOUT_MS = 3000;
    private static final int PING_TIMEOUT_MS = 2000;

    private final DeviceRegistry registry;

    public DeviceUdpClient(DeviceRegistry registry) {
        this.registry = registry;
    }

    public void send(String refName, String json) throws Exception {
        SmartDevice device = registry.getByRefName(refName);
        if (device == null) {
            throw new Exception("Device not found: " + refName);
        }

        sendUdp(json, device.getInetAddress());
    }

    private void sendUdp(String json, InetAddress address) throws Exception {
        byte[] data = json.getBytes();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(SEND_TIMEOUT_MS);
            socket.send(new DatagramPacket(data, data.length, address, WIZ_PORT));

            byte[] buf = new byte[1024];
            DatagramPacket response = new DatagramPacket(buf, buf.length);
            socket.receive(response);

            String responseJson = new String(response.getData(), 0, response.getLength());
            try {
                new Gson().fromJson(responseJson, Object.class);
            } catch (JsonSyntaxException e) {
                throw new Exception("Device returned invalid JSON: " + responseJson, e);
            }
        }
    }

    public boolean ping(String refName) {
        SmartDevice device = registry.getByRefName(refName);
        if (device == null) {
            return false;
        }
        return ping(device.getInetAddress());
    }

    public static boolean ping(InetAddress address) {
        String payload = "{\"method\":\"getPilot\",\"params\":{}}";
        byte[] data = payload.getBytes();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(PING_TIMEOUT_MS);
            socket.send(new DatagramPacket(data, data.length, address, WIZ_PORT));
            DatagramPacket response = new DatagramPacket(new byte[1024], 1024);
            socket.receive(response);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
