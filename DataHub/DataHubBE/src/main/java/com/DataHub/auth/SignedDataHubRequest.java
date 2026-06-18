package com.DataHub.auth;

public record SignedDataHubRequest(String keyId, String timestamp, String signature, String canonicalPayload) {}
