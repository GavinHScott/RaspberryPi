package com.SmartDeviceManager.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.model.SmartDevice;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;
import com.SmartDeviceManager.registry.DeviceRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Manages sunrise sequences: fade and temperature ramp from 0→100 over N minutes.
 * Sends one combined UDP packet per second, only when values change.
 * Uses a logarithmic curve so the bulb stays dim longer before brightening rapidly,
 * mimicking a natural sunrise.
 */
@Service
public class SunriseService {

    private static final Logger log = LoggerFactory.getLogger(SunriseService.class);
    private static final double CURVE_EXPONENT = 2.5;
    private static final int MIN_VISIBLE_TRANSITION = 11;

    private final DeviceUdpClient udpClient;
    private final PayloadBuilder payloadBuilder;
    private final DeviceRegistry registry;
    private final NtfyNotificationService notifications;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> activeSunrises = new ConcurrentHashMap<>();

    public SunriseService(DeviceUdpClient udpClient, PayloadBuilder payloadBuilder,
                          DeviceRegistry registry, NtfyNotificationService notifications) {
        this.udpClient = udpClient;
        this.payloadBuilder = payloadBuilder;
        this.registry = registry;
        this.notifications = notifications;
    }

    @PostConstruct
    public void logStartup() {
        log.info("SunriseService loaded. Active sunrise scheduler thread pool size is 2.");
    }

    /**
     * Starts a sunrise sequence for the given device.
     * Cancels any previously running sunrise for that device.
     * Progress is mapped through a power curve (x^{@value CURVE_EXPONENT}) so brightness
     * increases slowly at first then accelerates, mimicking a real sunrise.
     *
     * @param refName friendly device name used by DeviceRegistry
     * @param minutes total duration of the sunrise
     */
    public void start(String refName, int minutes) {
        log.info("SunriseService start requested for {} over {} minutes", refName, minutes);
        SmartDevice device = registry.getByRefName(refName);
        if (device == null) {
            log.error("Device {} not found, aborting sunrise.", refName);
            notifications.send("SunriseService failed to start", "Bulb not found: " + refName);
            return;
        }

        log.info("SunriseService found {} ({}) at {}; pinging before start",
                device.getRefName(), device.getName(), device.getInetAddress().getHostAddress());
        if (!udpClient.ping(refName)) {
            log.error("Ping failed for {}, aborting sunrise.", refName);
            notifications.send("SunriseService failed to start", refName + " was found but did not respond to ping.");
            return;
        }
        log.info("SunriseService ping succeeded for {}", refName);

        cancel(refName);

        int totalSeconds = minutes * 60;
        int[] elapsed = {0};
        int[] lastTransition = {-1};
        AtomicBoolean stepFailed = new AtomicBoolean(false);

        notifications.send("SunriseService started",
                "SunriseService found and connected to " + refName + ". Running for " + minutes + " minutes.");
        log.info("SunriseService scheduling {} second sunrise for {}", totalSeconds, refName);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (elapsed[0] > totalSeconds) {
                    String status = stepFailed.get() ? "finished with errors" : "completed successfully";
                    log.info("SunriseService {} for {}. Final transition: {}",
                            status, refName, lastTransition[0]);
                    notifications.send("SunriseService finished",
                            "SunriseService " + status + " for " + refName + ". Final transition: " + lastTransition[0] + ".");
                    cancel(refName);
                    return;
                }

                float linear = (float) elapsed[0] / totalSeconds;
                float curved = (float) Math.pow(linear, CURVE_EXPONENT);

                int transitionValue = Math.max(MIN_VISIBLE_TRANSITION, Math.round(curved * 100));

                if (transitionValue != lastTransition[0]) {
                    DeviceCommand command = new DeviceCommand(refName, "transition", List.of(String.valueOf(transitionValue)));
                    String payload = payloadBuilder.build(command);
                    udpClient.send(refName, payload);
                    lastTransition[0] = transitionValue;
                    if (elapsed[0] == 0 || elapsed[0] % 300 == 0 || transitionValue == 100) {
                        log.info("SunriseService progress for {}: elapsed={}s/{}s transition={}",
                                refName, elapsed[0], totalSeconds, transitionValue);
                    }
                }
            } catch (Exception e) {
                stepFailed.set(true);
                log.error("SunriseService step failed for {} at elapsed {}s: {}", refName, elapsed[0], e.getMessage());
            } finally {
                elapsed[0]++;
            }
        }, 0, 1, TimeUnit.SECONDS);

        activeSunrises.put(refName, future);
    }

    /**
     * Cancels any running sunrise for the given device.
     */
    public void cancel(String refName) {
        ScheduledFuture<?> existing = activeSunrises.remove(refName);
        if (existing != null) {
            existing.cancel(false);
            log.info("SunriseService cancelled active sunrise for {}", refName);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("SunriseService shutting down scheduler");
        scheduler.shutdownNow();
    }
}
