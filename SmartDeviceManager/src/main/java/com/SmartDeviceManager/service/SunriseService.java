package com.SmartDeviceManager.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.SmartDeviceManager.model.DeviceCommand;
import com.SmartDeviceManager.network.DeviceUdpClient;
import com.SmartDeviceManager.payload.PayloadBuilder;

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

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> activeSunrises = new ConcurrentHashMap<>();

    public SunriseService(DeviceUdpClient udpClient, PayloadBuilder payloadBuilder) {
        this.udpClient = udpClient;
        this.payloadBuilder = payloadBuilder;
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
        if (!udpClient.ping(refName)) {
            log.error("Ping failed for {}, aborting sunrise.", refName);
            return;
        }

        cancel(refName);

        int totalSeconds = minutes * 60;
        int[] elapsed = {0};
        int[] lastTransition = {-1};

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (elapsed[0] > totalSeconds) {
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
                }
            } catch (Exception e) {
                log.error("Step failed for {}: {}", refName, e.getMessage());
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
        }
    }
}
