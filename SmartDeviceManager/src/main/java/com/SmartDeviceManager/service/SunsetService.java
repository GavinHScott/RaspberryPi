package com.SmartDeviceManager.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class SunsetService {

    private static final Logger log = LoggerFactory.getLogger(SunsetService.class);
    private static final LocalTime DETECTION_START = LocalTime.of(21, 0);
    private static final LocalTime DETECTION_END = LocalTime.MIDNIGHT;
    private static final Duration SHORT_OUTAGE_THRESHOLD = Duration.ofSeconds(10);
    private static final int RECOVERY_FADE = 80;
    private static final int RECOVERY_TEMP = 50;

    private final DeviceRegistry registry;
    private final DeviceHealthService deviceHealthService;
    private final DeviceUdpClient udpClient;
    private final PayloadBuilder payloadBuilder;
    private final NtfyNotificationService notifications;
    private final Clock clock;
    private final ExecutorService pingExecutor;
    private final Map<String, Boolean> lastKnownOnline = new ConcurrentHashMap<>();
    private final Map<String, Instant> offlineSince = new ConcurrentHashMap<>();
    private final Map<String, Boolean> observedOfflineCycle = new ConcurrentHashMap<>();
    private volatile boolean monitoringWindowOpen;
    private volatile boolean firstSchedulerRunLogged;
    private volatile boolean waitingForWindowLogged;

    @Autowired
    public SunsetService(DeviceRegistry registry, DeviceHealthService deviceHealthService,
                         DeviceUdpClient udpClient, PayloadBuilder payloadBuilder,
                         NtfyNotificationService notifications) {
        this(registry, deviceHealthService, udpClient, payloadBuilder, notifications,
                Clock.systemDefaultZone(), Executors.newVirtualThreadPerTaskExecutor());
    }

    SunsetService(DeviceRegistry registry, DeviceHealthService deviceHealthService,
                  DeviceUdpClient udpClient, PayloadBuilder payloadBuilder,
                  NtfyNotificationService notifications,
                  Clock clock, ExecutorService pingExecutor) {
        this.registry = registry;
        this.deviceHealthService = deviceHealthService;
        this.udpClient = udpClient;
        this.payloadBuilder = payloadBuilder;
        this.notifications = notifications;
        this.clock = clock;
        this.pingExecutor = pingExecutor;
    }

    @PostConstruct
    public void logStartup() {
        log.info("Sunset service loaded. Detection window: {} to {} local time. Ping interval controls scheduler delay.",
                DETECTION_START, DETECTION_END);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runSunsetChecksOnStartup() {
        try {
            runSunsetChecks("startup");
        } catch (Exception e) {
            log.error("Sunset startup check failed: {}", e.getMessage(), e);
            notifications.send("Sunset startup check failed",
                    "Sunset startup check failed: " + e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${device.health.ping-interval-ms:5000}", initialDelayString = "0")
    public void runScheduledSunsetChecks() {
        try {
            runSunsetChecks("scheduler");
        } catch (Exception e) {
            log.error("Sunset scheduled check failed: {}", e.getMessage(), e);
            notifications.send("Sunset scheduled check failed",
                    "Sunset scheduled check failed: " + e.getMessage());
        }
    }

    private synchronized void runSunsetChecks(String trigger) {
        LocalTime currentTime = LocalTime.now(clock);
        boolean detectionWindowOpen = isDetectionWindowOpen();

        if (!firstSchedulerRunLogged) {
            firstSchedulerRunLogged = true;
            log.info("Sunset checks running. First trigger: {}, current local time: {}.",
                    trigger, currentTime);
        }

        if (!detectionWindowOpen) {
            if (!waitingForWindowLogged) {
                waitingForWindowLogged = true;
                log.info("Waiting for sunset detection window. Current local time: {}; starts at {}.",
                        currentTime, DETECTION_START);
            }
            closeMonitoringWindow();
            return;
        }

        List<SmartDevice> monitoredDevices = registry.getAll().stream()
                .filter(SmartDevice::isOfflineDetection)
                .toList();
        openMonitoringWindow(monitoredDevices, trigger);

        List<CompletableFuture<Void>> pings = monitoredDevices.stream()
                .map(device -> CompletableFuture.runAsync(() -> runSunsetCheck(device), pingExecutor))
                .toList();

        CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new)).join();
    }

    private void runSunsetCheck(SmartDevice device) {
        Instant checkedAt = Instant.now(clock);
        boolean online = deviceHealthService.updateDeviceHealth(device, checkedAt);

        Boolean previousOnline = lastKnownOnline.put(device.getRefName(), online);
        if (previousOnline == null) {
            if (!online) {
                offlineSince.put(device.getRefName(), checkedAt);
                observedOfflineCycle.put(device.getRefName(), false);
                log.warn("First sunset check: {} is offline", device.getRefName());
            } else {
                log.info("First sunset check: {} is online; sending nightly off", device.getRefName());
                sendNightlyOff(device, "first online check after 21:00");
            }
            return;
        }

        if (previousOnline && !online) {
            offlineSince.put(device.getRefName(), checkedAt);
            observedOfflineCycle.put(device.getRefName(), true);
            log.warn("Device {} switched offline", device.getRefName());
            return;
        }

        if (!previousOnline && online) {
            Instant wentOfflineAt = offlineSince.remove(device.getRefName());
            Duration outageDuration = wentOfflineAt == null ? Duration.ZERO : Duration.between(wentOfflineAt, checkedAt);
            log.info("Device {} switched online after {} ms offline",
                    device.getRefName(), outageDuration.toMillis());

            boolean wasObservedCycle = Boolean.TRUE.equals(observedOfflineCycle.remove(device.getRefName()));
            if (wasObservedCycle && outageDuration.compareTo(SHORT_OUTAGE_THRESHOLD) < 0) {
                sendShortOutageRecovery(device);
            } else {
                sendNightlyOff(device, "online after extended or unobserved outage");
            }
        }
    }

    private boolean isDetectionWindowOpen() {
        LocalTime now = LocalTime.now(clock);
        if (DETECTION_START.equals(DETECTION_END)) {
            return true;
        }

        if (DETECTION_START.isBefore(DETECTION_END)) {
            return !now.isBefore(DETECTION_START) && now.isBefore(DETECTION_END);
        }

        return !now.isBefore(DETECTION_START) || now.isBefore(DETECTION_END);
    }

    private void clearState() {
        lastKnownOnline.clear();
        offlineSince.clear();
        observedOfflineCycle.clear();
    }

    private String describeDevices(List<SmartDevice> devices) {
        return devices.stream()
                .map(device -> device.getRefName() + " (" + device.getName() + ", "
                        + device.getInetAddress().getHostAddress() + ")")
                .toList()
                .toString();
    }

    private void openMonitoringWindow(List<SmartDevice> monitoredDevices, String trigger) {
        if (monitoringWindowOpen) {
            return;
        }

        waitingForWindowLogged = false;
        monitoringWindowOpen = true;
        log.info("Detection window opened by {} at {} with {} monitored devices: {}",
                trigger, LocalTime.now(clock), monitoredDevices.size(), describeDevices(monitoredDevices));
        if (monitoredDevices.isEmpty()) {
            log.warn("No devices configured with offlineDetection=true; no sunset action will run.");
        }
        deviceHealthService.startMonitoring(monitoredDevices);
    }

    private void closeMonitoringWindow() {
        if (!monitoringWindowOpen) {
            clearState();
            return;
        }

        monitoringWindowOpen = false;
        log.info("Detection window closed at {}. Clearing sunset state.", LocalTime.now(clock));
        clearState();
        deviceHealthService.stopMonitoring();
    }

    private void sendShortOutageRecovery(SmartDevice device) {
        notifications.send("Short-outage recovery started",
                "Sending short-outage recovery to " + device.getRefName() + ".");
        try {
            String payload = payloadBuilder.buildTempFade(RECOVERY_FADE, RECOVERY_TEMP);
            udpClient.send(device.getRefName(), payload);
            log.info("Sent short-outage recovery to {} (temp={}, fade={})",
                    device.getRefName(), RECOVERY_TEMP, RECOVERY_FADE);
            notifications.send("Short-outage recovery succeeded",
                    device.getRefName() + " set to temp " + RECOVERY_TEMP + " and fade " + RECOVERY_FADE + ".");
        } catch (Exception e) {
            log.error("Short-outage recovery failed for {}: {}", device.getRefName(), e.getMessage());
            notifications.send("Short-outage recovery failed",
                    "Short-outage recovery failed for " + device.getRefName() + ": " + e.getMessage());
        }
    }

    private void sendNightlyOff(SmartDevice device, String reason) {
        notifications.send("Nightly off started",
                "Sending nightly off to " + device.getRefName() + " (" + reason + ").");
        try {
            String payload = payloadBuilder.build(new DeviceCommand(device.getRefName(), "off", List.of()));
            udpClient.send(device.getRefName(), payload);
            log.info("Sent nightly off to {} (reason: {})", device.getRefName(), reason);
            notifications.send("Nightly off succeeded",
                    device.getRefName() + " confirmed off.");
        } catch (Exception e) {
            log.error("Nightly off failed for {}: {}", device.getRefName(), e.getMessage());
            notifications.send("Nightly off failed",
                    "Nightly off failed for " + device.getRefName() + ": " + e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        pingExecutor.close();
    }
}
