package com.DataHub.auth;

import java.util.Set;

public record DataHubPairingRequest(
        String clientName,
        String keyId,
        String publicKey,
        Set<DataHubPermission> permissions,
        String yubiKeyProof) {
}
