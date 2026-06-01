package com.SmartDeviceManager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartDeviceManager {

    public static void main(String[] args) {
        SpringApplication.run(SmartDeviceManager.class, args);
    }
}
