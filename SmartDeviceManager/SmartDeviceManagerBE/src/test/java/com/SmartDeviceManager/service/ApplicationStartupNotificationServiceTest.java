package com.SmartDeviceManager.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;

class ApplicationStartupNotificationServiceTest {

    private final NtfyNotificationService notifications = mock(NtfyNotificationService.class);
    private final ApplicationStartupNotificationService service = new ApplicationStartupNotificationService(notifications);

    @Test
    void notifyApplicationReadySendsStartupNotification() {
        service.notifyApplicationReady();

        verify(notifications).send(eq("SmartDeviceManager started"), contains("SmartDeviceManager started successfully"));
    }

    @Test
    void logApplicationShutdownOnlyLogs() {
        service.logApplicationShutdown();

        verifyNoInteractions(notifications);
    }
}
