package com.SmartDeviceManager.model;

import java.net.InetAddress;
import java.time.Instant;

public class SmartDevice {

    private final String refName;
    private final String name;
    private final InetAddress inetAddress;
    private final boolean offlineDetection;
    private volatile boolean isOnline;
    private volatile Instant lastSeen;

    public SmartDevice(String refName, String name, InetAddress inetAddress, boolean offlineDetection) {
        this.refName = refName;
        this.name = name;
        this.inetAddress = inetAddress;
        this.offlineDetection = offlineDetection;
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

    public boolean isOfflineDetection() {
        return offlineDetection;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
