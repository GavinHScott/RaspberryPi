package com.DataHub.auth;

import java.util.Set;

public record DataHubClient(String name, String keyId, String publicKey, Set<DataHubPermission> permissions) {

    public boolean canRead() {
        return permissions.contains(DataHubPermission.READ) || permissions.contains(DataHubPermission.WRITE);
    }

    public boolean canWrite() {
        return permissions.contains(DataHubPermission.WRITE);
    }
}
