package com.SmartDeviceManager.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class ScheduledTasksTest {

    private final CommandParser commandParser = mock(CommandParser.class);
    private final NtfyNotificationService notifications = mock(NtfyNotificationService.class);
    private final ScheduledTasks tasks = new ScheduledTasks(commandParser, notifications);

    @Test
    void morningAlarmSendsNotificationAndRunsCommand() throws Exception {
        tasks.morningAlarm();

        verify(notifications).send("Morning alarm triggered",
                "Started morning alarm command: bedroomlight sunrise 60");
        verify(commandParser).parseAndExecute("bedroomlight sunrise 60");
    }

    @Test
    void morningAlarmSendsFailureNotificationWhenCommandFails() throws Exception {
        org.mockito.Mockito.doThrow(new Exception("boom\n")).when(commandParser).parseAndExecute("bedroomlight sunrise 60");

        tasks.morningAlarm();

        verify(notifications).send("Morning alarm failed", "Morning alarm command failed: boom");
    }
}
