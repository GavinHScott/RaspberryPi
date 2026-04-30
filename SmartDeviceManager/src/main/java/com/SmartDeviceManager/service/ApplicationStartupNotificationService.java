package com.SmartDeviceManager.service;

import java.time.Instant;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class ApplicationStartupNotificationService {

    private final NtfyNotificationService notifications;

    public ApplicationStartupNotificationService(NtfyNotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void notifyApplicationReady() {
        notifications.send("SmartDeviceManager started",
                "ApplicationStartupNotificationService reports SmartDeviceManager started successfully at "
                        + Instant.now() + ".");
    }
}
