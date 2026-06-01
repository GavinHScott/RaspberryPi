package com.SmartDeviceManager;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.SmartDeviceManager.model.SmartDevice;

public final class TestDevices {

    private TestDevices() {}

    public static SmartDevice device(String refName, String name) {
        return device(refName, name, true);
    }

    public static SmartDevice device(String refName, String name, boolean offlineDetection) {
        try {
            return new SmartDevice(refName, name, InetAddress.getByName("127.0.0.1"), offlineDetection);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
