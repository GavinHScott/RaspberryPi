package com.DataHub.resolver;

import java.util.Map;

public record DataHubQueryRequest(String query, Map<String, Object> params) {}
