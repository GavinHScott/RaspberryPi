package com.DataHub.resolver;

import java.util.List;
import java.util.Map;

public record DataHubQueryResponse(List<Map<String, Object>> rows, int rowCount) {}
