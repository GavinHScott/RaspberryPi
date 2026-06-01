package com.DataHub.resolver;

import java.util.Map;

public record DataHubWriteRequest(String command, Map<String, Object> params) {}
