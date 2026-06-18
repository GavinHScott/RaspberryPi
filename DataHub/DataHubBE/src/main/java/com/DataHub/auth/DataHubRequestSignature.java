package com.DataHub.auth;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class DataHubRequestSignature {

    private DataHubRequestSignature() {}

    public static String canonicalReadPayload(String timestamp, String query, Map<String, Object> params) {
        return canonicalPayload("READ", timestamp, query, params);
    }

    public static String canonicalWritePayload(String timestamp, String command, Map<String, Object> params) {
        return canonicalPayload("WRITE", timestamp, command, params);
    }

    private static String canonicalPayload(String operation, String timestamp, String statement, Map<String, Object> params) {
        return operation + "\n"
                + nullToEmpty(timestamp) + "\n"
                + nullToEmpty(statement).strip() + "\n"
                + canonicalParams(params);
    }

    private static String canonicalParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        return new TreeMap<>(params).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + String.valueOf(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
