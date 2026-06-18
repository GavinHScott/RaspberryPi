package com.DataHub.auth;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DataHubKeyRegistry {

    private final Map<String, DataHubClient> clientsByKeyId = new ConcurrentHashMap<>();

    public DataHubKeyRegistry(@Value("${datahub.auth.clients:}") String configuredClients) {
        parseClients(configuredClients).forEach(client -> clientsByKeyId.put(client.keyId(), client));
    }

    public DataHubClient getClient(String keyId) {
        return clientsByKeyId.get(keyId);
    }

    void registerClient(DataHubClient client) {
        clientsByKeyId.put(client.keyId(), client);
    }

    private Set<DataHubClient> parseClients(String configuredClients) {
        if (configuredClients == null || configuredClients.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(configuredClients.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isBlank())
                .map(this::parseClientEntry)
                .collect(Collectors.toUnmodifiableSet());
    }

    private DataHubClient parseClientEntry(String entry) {
        String[] parts = entry.split(":", 4);
        if (parts.length != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid datahub.auth.clients entry. Expected appName:keyId:base64PublicKey:READ|WRITE");
        }

        Set<DataHubPermission> permissions = Arrays.stream(parts[3].split("\\|"))
                .map(String::strip)
                .filter(permission -> !permission.isBlank())
                .map(permission -> DataHubPermission.valueOf(permission.toUpperCase()))
                .collect(Collectors.toUnmodifiableSet());

        return new DataHubClient(parts[0], parts[1], parts[2], permissions);
    }
}
