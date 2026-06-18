package com.DataHub.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.DataHub.auth.DataHubAuthService;
import com.DataHub.auth.DataHubClient;
import com.DataHub.auth.DataHubPairingRequest;
import com.DataHub.auth.DataHubPermission;
import com.DataHub.auth.DataHubRequestSignature;
import com.DataHub.auth.SignedDataHubRequest;
import com.DataHub.auth.YubiKeyProvisioningService;
import com.DataHub.resolver.DataHubQueryRequest;
import com.DataHub.resolver.DataHubQueryResponse;
import com.DataHub.resolver.DataHubResolver;
import com.DataHub.resolver.DataHubWriteRequest;
import com.DataHub.resolver.DataHubWriteResponse;

class DataHubControllerTest {

    private final DataHubResolver resolver = mock(DataHubResolver.class);
    private final DataHubAuthService authService = mock(DataHubAuthService.class);
    private final YubiKeyProvisioningService provisioningService = mock(YubiKeyProvisioningService.class);
    private final DataHubController controller = new DataHubController(resolver, authService, provisioningService);

    @Test
    void healthReturnsOk() {
        assertEquals("DataHub OK", controller.health().getBody());
    }

    @Test
    void pairRegistersClientThroughYubiKeyProvisioning() {
        DataHubPairingRequest request = new DataHubPairingRequest(
                "KitchenPi", "kitchenpi-main", "public-key", Set.of(DataHubPermission.READ), "proof");
        DataHubClient client = new DataHubClient(
                "KitchenPi", "kitchenpi-main", "public-key", Set.of(DataHubPermission.READ));
        when(provisioningService.pairWithYubiKey(
                "KitchenPi", "kitchenpi-main", "public-key", Set.of(DataHubPermission.READ), "proof"))
                .thenReturn(client);

        var response = controller.pair(request);

        assertEquals("KitchenPi", response.getBody().clientName());
        assertEquals("kitchenpi-main", response.getBody().keyId());
        assertEquals(Set.of(DataHubPermission.READ), response.getBody().permissions());
    }

    @Test
    void resolveUsesReadOnlyResolverPath() {
        DataHubQueryRequest request = new DataHubQueryRequest("select 1", Map.of());
        DataHubQueryResponse response = new DataHubQueryResponse(List.of(), 0);
        when(resolver.resolveReadOnly(request)).thenReturn(response);

        assertEquals(response, controller.resolve("key", "2026-06-01T12:00:00Z", "signature", request).getBody());

        ArgumentCaptor<SignedDataHubRequest> signedRequest = ArgumentCaptor.forClass(SignedDataHubRequest.class);
        verify(authService).requireRead(signedRequest.capture());
        assertEquals("key", signedRequest.getValue().keyId());
        assertEquals("2026-06-01T12:00:00Z", signedRequest.getValue().timestamp());
        assertEquals("signature", signedRequest.getValue().signature());
        assertEquals(
                DataHubRequestSignature.canonicalReadPayload("2026-06-01T12:00:00Z", "select 1", Map.of()),
                signedRequest.getValue().canonicalPayload());
        verify(resolver).resolveReadOnly(request);
    }

    @Test
    void writeUsesWriteResolverPath() {
        DataHubWriteRequest request = new DataHubWriteRequest("insert into t values (:id)", java.util.Map.of("id", 1));
        DataHubWriteResponse response = new DataHubWriteResponse(1);
        when(resolver.resolveWrite(request)).thenReturn(response);

        assertEquals(response, controller.write("key", "2026-06-01T12:00:00Z", "signature", request).getBody());

        ArgumentCaptor<SignedDataHubRequest> signedRequest = ArgumentCaptor.forClass(SignedDataHubRequest.class);
        verify(authService).requireWrite(signedRequest.capture());
        assertEquals(
                DataHubRequestSignature.canonicalWritePayload(
                        "2026-06-01T12:00:00Z", "insert into t values (:id)", java.util.Map.of("id", 1)),
                signedRequest.getValue().canonicalPayload());
        verify(resolver).resolveWrite(request);
    }

    @Test
    void badRequestReturnsMessage() {
        var response = controller.badRequest(new IllegalArgumentException("only read-only SELECT queries are allowed"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("only read-only SELECT queries are allowed", response.getBody());
    }

    @Test
    void unauthorizedReturnsMessage() {
        var response = controller.unauthorized(new SecurityException("invalid bearer token"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("invalid bearer token", response.getBody());
    }
}
