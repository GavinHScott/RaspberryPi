package com.DataHub.auth;

import java.util.Set;

public record DataHubPairingResponse(
        String clientName,
        String keyId,
        Set<DataHubPermission> permissions) {

    public static DataHubPairingResponse fromClient(DataHubClient client) {
        return new DataHubPairingResponse(client.name(), client.keyId(), client.permissions());
    }
}
