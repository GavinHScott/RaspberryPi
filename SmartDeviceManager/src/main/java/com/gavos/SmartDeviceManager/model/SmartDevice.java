package com.gavos.SmartDeviceManager.model;

import java.net.InetAddress;

public class SmartDevice {

    private final String refName;
    private final String name;
    private final InetAddress inetAddress;

    public SmartDevice(String refName, String name, InetAddress inetAddress) {
        this.refName = refName;
        this.name = name;
        this.inetAddress = inetAddress;
    }

    public String getName() {
        return name;
    }

    public String getRefName() {
        return refName;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }
}
