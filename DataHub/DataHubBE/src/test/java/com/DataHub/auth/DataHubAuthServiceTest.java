package com.DataHub.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DataHubAuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void acceptsSignedReadRequestForReadClient() throws Exception {
        KeyPair keyPair = keyPair();
        DataHubAuthService service = service("Reader", "reader-key", keyPair, "READ");
        String timestamp = NOW.toString();
        String payload = DataHubRequestSignature.canonicalReadPayload(timestamp, "select 1", Map.of());

        DataHubClient client = service.requireRead(new SignedDataHubRequest(
                "reader-key", timestamp, sign(keyPair, payload), payload));

        assertEquals("Reader", client.name());
        assertTrue(client.canRead());
        assertThrows(SecurityException.class, () -> service.requireWrite(new SignedDataHubRequest(
                "reader-key", timestamp, sign(keyPair, payload), payload)));
    }

    @Test
    void acceptsSignedWriteRequestForWriteClient() throws Exception {
        KeyPair keyPair = keyPair();
        DataHubAuthService service = service("Writer", "writer-key", keyPair, "WRITE");
        String timestamp = NOW.toString();
        String payload = DataHubRequestSignature.canonicalWritePayload(timestamp, "insert into things values (:id)", Map.of("id", 1));
        SignedDataHubRequest request = new SignedDataHubRequest("writer-key", timestamp, sign(keyPair, payload), payload);

        assertTrue(service.requireRead(request).canRead());
        assertTrue(service.requireWrite(request).canWrite());
    }

    @Test
    void rejectsMissingMalformedUnknownExpiredAndInvalidSignatures() throws Exception {
        KeyPair keyPair = keyPair();
        DataHubAuthService service = service("Reader", "reader-key", keyPair, "READ");
        String timestamp = NOW.toString();
        String payload = DataHubRequestSignature.canonicalReadPayload(timestamp, "select 1", Map.of());
        String signature = sign(keyPair, payload);

        assertThrows(SecurityException.class, () -> service.requireRead(null));
        assertThrows(SecurityException.class, () -> service.requireRead(new SignedDataHubRequest(null, timestamp, signature, payload)));
        assertThrows(SecurityException.class, () -> service.requireRead(new SignedDataHubRequest("unknown", timestamp, signature, payload)));
        assertThrows(SecurityException.class, () -> service.requireRead(new SignedDataHubRequest("reader-key", "bad", signature, payload)));
        assertThrows(SecurityException.class, () -> service.requireRead(new SignedDataHubRequest(
                "reader-key", NOW.minusSeconds(600).toString(), signature, payload)));
        assertThrows(SecurityException.class, () -> service.requireRead(new SignedDataHubRequest(
                "reader-key", timestamp, signature, payload + "tampered")));
    }

    @Test
    void rejectsMalformedConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new DataHubKeyRegistry("broken"));
    }

    private DataHubAuthService service(String name, String keyId, KeyPair keyPair, String permissions) {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        DataHubKeyRegistry registry = new DataHubKeyRegistry(name + ":" + keyId + ":" + publicKey + ":" + permissions);
        return new DataHubAuthService(registry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private String sign(KeyPair keyPair, String payload) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }
}
