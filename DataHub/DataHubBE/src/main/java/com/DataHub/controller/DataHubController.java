package com.DataHub.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.DataHub.auth.DataHubAuthService;
import com.DataHub.auth.DataHubClient;
import com.DataHub.auth.DataHubPairingRequest;
import com.DataHub.auth.DataHubPairingResponse;
import com.DataHub.auth.DataHubRequestSignature;
import com.DataHub.auth.SignedDataHubRequest;
import com.DataHub.auth.YubiKeyProvisioningService;
import com.DataHub.resolver.DataHubQueryRequest;
import com.DataHub.resolver.DataHubQueryResponse;
import com.DataHub.resolver.DataHubResolver;
import com.DataHub.resolver.DataHubWriteRequest;
import com.DataHub.resolver.DataHubWriteResponse;

@RestController
public class DataHubController {

    private final DataHubResolver resolver;
    private final DataHubAuthService authService;
    private final YubiKeyProvisioningService provisioningService;

    public DataHubController(
            DataHubResolver resolver,
            DataHubAuthService authService,
            YubiKeyProvisioningService provisioningService) {
        this.resolver = resolver;
        this.authService = authService;
        this.provisioningService = provisioningService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("DataHub OK");
    }

    @PostMapping("/pair")
    public ResponseEntity<DataHubPairingResponse> pair(@RequestBody DataHubPairingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("pairing request is required");
        }

        DataHubClient client = provisioningService.pairWithYubiKey(
                request.clientName(),
                request.keyId(),
                request.publicKey(),
                request.permissions(),
                request.yubiKeyProof());
        return ResponseEntity.ok(DataHubPairingResponse.fromClient(client));
    }

    @PostMapping("/resolve")
    public ResponseEntity<DataHubQueryResponse> resolve(
            @RequestHeader(value = "X-DataHub-Key-Id", required = false) String keyId,
            @RequestHeader(value = "X-DataHub-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-DataHub-Signature", required = false) String signature,
            @RequestBody DataHubQueryRequest request) {
        authService.requireRead(new SignedDataHubRequest(
                keyId,
                timestamp,
                signature,
                DataHubRequestSignature.canonicalReadPayload(
                        timestamp,
                        request == null ? null : request.query(),
                        request == null ? null : request.params())));
        return ResponseEntity.ok(resolver.resolveReadOnly(request));
    }

    @PostMapping("/write")
    public ResponseEntity<DataHubWriteResponse> write(
            @RequestHeader(value = "X-DataHub-Key-Id", required = false) String keyId,
            @RequestHeader(value = "X-DataHub-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-DataHub-Signature", required = false) String signature,
            @RequestBody DataHubWriteRequest request) {
        authService.requireWrite(new SignedDataHubRequest(
                keyId,
                timestamp,
                signature,
                DataHubRequestSignature.canonicalWritePayload(
                        timestamp,
                        request == null ? null : request.command(),
                        request == null ? null : request.params())));
        return ResponseEntity.ok(resolver.resolveWrite(request));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> unauthorized(SecurityException exception) {
        return ResponseEntity.status(401).body(exception.getMessage());
    }
}
