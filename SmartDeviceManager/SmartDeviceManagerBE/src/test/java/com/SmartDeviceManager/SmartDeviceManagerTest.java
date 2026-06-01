package com.SmartDeviceManager;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class SmartDeviceManagerTest {

    @Test
    void mainStartsSpringApplication() {
        String[] args = {"--server.port=0"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            SmartDeviceManager.main(args);

            springApplication.verify(() -> SpringApplication.run(SmartDeviceManager.class, args));
        }
    }
}
