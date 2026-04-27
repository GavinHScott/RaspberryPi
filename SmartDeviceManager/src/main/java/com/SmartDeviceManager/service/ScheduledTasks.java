package com.SmartDeviceManager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private final CommandParser commandParser;

    public ScheduledTasks(CommandParser commandParser) {
        this.commandParser = commandParser;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void morningAlarm() {
        try {
            commandParser.parseAndExecute("bedroomlight sunrise 60");
        } catch (Exception e) {
            log.error("morningAlarm failed: {}", e.getMessage());
        }
    }
}
