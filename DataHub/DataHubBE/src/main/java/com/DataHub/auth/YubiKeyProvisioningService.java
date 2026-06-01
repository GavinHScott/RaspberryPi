package com.DataHub.auth;

import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class YubiKeyProvisioningService {

    private final DataHubKeyRegistry keyRegistry;
    private final boolean pairingEnabled;
    private final String acceptedPairingProof;

    public YubiKeyProvisioningService(
            DataHubKeyRegistry keyRegistry,
            @Value("${datahub.auth.yubikey.enabled:false}") boolean pairingEnabled,
            @Value("${datahub.auth.yubikey.accepted-proof:}") String acceptedPairingProof) {
        this.keyRegistry = keyRegistry;
        this.pairingEnabled = pairingEnabled;
        this.acceptedPairingProof = acceptedPairingProof == null ? "" : acceptedPairingProof;
    }

    public DataHubClient pairWithYubiKey(String clientName, String keyId, String publicKey,
                                         Set<DataHubPermission> permissions, String yubiKeyProof) {
        validatePairingRequest(clientName, keyId, publicKey, permissions);
        if (!isValidYubiKeyProof(yubiKeyProof)) {
            throw new SecurityException("valid YubiKey proof is required to pair a new key");
        }

        DataHubClient client = new DataHubClient(clientName, keyId, publicKey, permissions);
        keyRegistry.registerClient(client);
        return client;
    }

    private boolean isValidYubiKeyProof(String yubiKeyProof) {
        if (!pairingEnabled) {
            return false;
        }
        if (acceptedPairingProof.isBlank()) {
            return false;
        }
        return Objects.equals(acceptedPairingProof, yubiKeyProof);
    }

    private void validatePairingRequest(String clientName, String keyId, String publicKey,
                                        Set<DataHubPermission> permissions) {
        if (clientName == null || clientName.isBlank()) {
            throw new IllegalArgumentException("clientName is required");
        }
        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("keyId is required");
        }
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("publicKey is required");
        }
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("at least one permission is required");
        }
    }
}
