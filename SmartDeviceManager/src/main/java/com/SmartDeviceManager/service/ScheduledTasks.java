package com.SmartDeviceManager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private static final String MORNING_ALARM_COMMAND = "bedroomlight sunrise 60";

    private final CommandParser commandParser;
    private final NtfyNotificationService notifications;

    public ScheduledTasks(CommandParser commandParser, NtfyNotificationService notifications) {
        this.commandParser = commandParser;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void morningAlarm() {
        notifications.send("ScheduledTasks morning sunrise triggered",
                "ScheduledTasks started command: " + MORNING_ALARM_COMMAND);
        try {
            commandParser.parseAndExecute(MORNING_ALARM_COMMAND);
        } catch (Exception e) {
            log.error("morningAlarm failed: {}", e.getMessage());
            notifications.send("ScheduledTasks morning sunrise failed",
                    "ScheduledTasks command failed: " + e.getMessage().strip());
        }
    }
}
