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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

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
    private final Clock clock;
    private final ExecutorService pingExecutor;
    private final Map<String, Boolean> lastKnownOnline = new ConcurrentHashMap<>();
    private final Map<String, Instant> offlineSince = new ConcurrentHashMap<>();
    private final Map<String, Boolean> observedOfflineCycle = new ConcurrentHashMap<>();

    @Autowired
    public SunsetService(DeviceRegistry registry, DeviceHealthService deviceHealthService,
                         DeviceUdpClient udpClient, PayloadBuilder payloadBuilder) {
        this(registry, deviceHealthService, udpClient, payloadBuilder,
                Clock.systemDefaultZone(), Executors.newVirtualThreadPerTaskExecutor());
    }

    SunsetService(DeviceRegistry registry, DeviceHealthService deviceHealthService,
                  DeviceUdpClient udpClient, PayloadBuilder payloadBuilder,
                  Clock clock, ExecutorService pingExecutor) {
        this.registry = registry;
        this.deviceHealthService = deviceHealthService;
        this.udpClient = udpClient;
        this.payloadBuilder = payloadBuilder;
        this.clock = clock;
        this.pingExecutor = pingExecutor;
    }

    @Scheduled(fixedDelayString = "${device.health.ping-interval-ms:5000}")
    public void runSunsetChecks() {
        if (!isDetectionWindowOpen()) {
            clearState();
            return;
        }

        List<CompletableFuture<Void>> pings = registry.getAll().stream()
                .filter(SmartDevice::isOfflineDetection)
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
            } else {
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
        return !now.isBefore(DETECTION_START) && now.isBefore(DETECTION_END);
    }

    private void clearState() {
        lastKnownOnline.clear();
        offlineSince.clear();
        observedOfflineCycle.clear();
    }

    private void sendShortOutageRecovery(SmartDevice device) {
        try {
            String payload = payloadBuilder.buildTempFade(RECOVERY_FADE, RECOVERY_TEMP);
            udpClient.send(device.getRefName(), payload);
            log.info("Sent short-outage recovery to {}: temp {}, fade {}",
                    device.getRefName(), RECOVERY_TEMP, RECOVERY_FADE);
        } catch (Exception e) {
            log.error("Short-outage recovery failed for {}: {}", device.getRefName(), e.getMessage());
        }
    }

    private void sendNightlyOff(SmartDevice device, String reason) {
        try {
            String payload = payloadBuilder.build(new DeviceCommand(device.getRefName(), "off", List.of()));
            udpClient.send(device.getRefName(), payload);
            log.info("Sent nightly off to {} ({})", device.getRefName(), reason);
        } catch (Exception e) {
            log.error("Nightly off failed for {}: {}", device.getRefName(), e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        pingExecutor.close();
    }
}
