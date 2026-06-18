package com.DataHub.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DataHubAuthService {

    private static final Duration SIGNATURE_TTL = Duration.ofMinutes(5);

    private final DataHubKeyRegistry keyRegistry;
    private final Clock clock;

    @Autowired
    public DataHubAuthService(DataHubKeyRegistry keyRegistry) {
        this(keyRegistry, Clock.systemUTC());
    }

    DataHubAuthService(DataHubKeyRegistry keyRegistry, Clock clock) {
        this.keyRegistry = keyRegistry;
        this.clock = clock;
    }

    public DataHubClient requireRead(SignedDataHubRequest request) {
        DataHubClient client = requireSignedClient(request);
        if (!client.canRead()) {
            throw new SecurityException("key does not have read access");
        }
        return client;
    }

    public DataHubClient requireWrite(SignedDataHubRequest request) {
        DataHubClient client = requireSignedClient(request);
        if (!client.canWrite()) {
            throw new SecurityException("key does not have write access");
        }
        return client;
    }

    private DataHubClient requireSignedClient(SignedDataHubRequest request) {
        if (request == null || request.keyId() == null || request.keyId().isBlank()) {
            throw new SecurityException("missing key id");
        }
        if (request.timestamp() == null || request.timestamp().isBlank()) {
            throw new SecurityException("missing signature timestamp");
        }
        if (request.signature() == null || request.signature().isBlank()) {
            throw new SecurityException("missing signature");
        }

        DataHubClient client = keyRegistry.getClient(request.keyId());
        if (client == null) {
            throw new SecurityException("unknown key id");
        }

        Instant signedAt;
        try {
            signedAt = Instant.parse(request.timestamp());
        } catch (Exception e) {
            throw new SecurityException("invalid signature timestamp");
        }

        Duration age = Duration.between(signedAt, Instant.now(clock)).abs();
        if (age.compareTo(SIGNATURE_TTL) > 0) {
            throw new SecurityException("signature timestamp is outside the allowed window");
        }

        if (!verifySignature(client.publicKey(), request.signature(), request.canonicalPayload())) {
            throw new SecurityException("invalid signature");
        }

        return client;
    }

    private boolean verifySignature(String encodedPublicKey, String encodedSignature, String canonicalPayload) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(encodedPublicKey);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(encodedSignature));
        } catch (Exception e) {
            return false;
        }
    }
}
