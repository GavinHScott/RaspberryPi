package com.SmartDeviceManager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class NtfyNotificationService {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationService.class);

    private final RestClient restClient;
    private final String topicUrl;

    public NtfyNotificationService(RestClient.Builder restClientBuilder,
                                   @Value("${notifications.ntfy.url}") String topicUrl) {
        this.restClient = restClientBuilder.build();
        this.topicUrl = topicUrl;
    }

    public void send(String title, String message) {
        try {
            restClient.post()
                    .uri(topicUrl)
                    .header("Title", title)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to send ntfy notification '{}': {}", title, e.getMessage());
        }
    }
}
