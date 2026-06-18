package com.SmartDeviceManager.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

class NtfyNotificationServiceTest {

    @Test
    void sendPostsNotificationToConfiguredTopic() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);
        RestClient.RequestBodyUriSpec request = mock(RestClient.RequestBodyUriSpec.class, org.mockito.Mockito.RETURNS_SELF);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        when(builder.build()).thenReturn(restClient);
        when(restClient.post()).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        new NtfyNotificationService(builder, "http://ntfy/topic").send("Title", "Body");

        verify(request).uri("http://ntfy/topic");
        verify(request).header("Title", "Title");
        verify(request).body("Body");
        verify(response).toBodilessEntity();
    }

    @Test
    void sendSwallowsRestClientFailures() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient restClient = mock(RestClient.class);
        when(builder.build()).thenReturn(restClient);
        when(restClient.post()).thenThrow(new RuntimeException("network down"));

        new NtfyNotificationService(builder, "http://ntfy/topic").send("Title", "Body");
    }
}
