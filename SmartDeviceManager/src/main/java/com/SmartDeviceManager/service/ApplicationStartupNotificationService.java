package com.SmartDeviceManager.service;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class ApplicationStartupNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationStartupNotificationService.class);

    private final NtfyNotificationService notifications;

    public ApplicationStartupNotificationService(NtfyNotificationService notifications) {
        this.notifications = notifications;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void notifyApplicationReady() {
        LOGGER.info("SmartDeviceManager startup complete at local time {} (UTC {}).", ZonedDateTime.now(), Instant.now());
        notifications.send("SmartDeviceManager started",
                "ApplicationStartupNotificationService reports SmartDeviceManager started successfully at local time "
                        + ZonedDateTime.now() + " (UTC " + Instant.now() + ").");
    }

    @PreDestroy
    public void logApplicationShutdown() {
        LOGGER.info("SmartDeviceManager shutting down at local time {} (UTC {}).", ZonedDateTime.now(), Instant.now());
    }
}
