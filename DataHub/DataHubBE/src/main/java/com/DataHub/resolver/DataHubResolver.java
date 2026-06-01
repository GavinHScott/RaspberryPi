package com.DataHub.resolver;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataHubResolver {

    private static final Pattern MULTI_STATEMENT = Pattern.compile(";");
    private static final Set<String> BLOCKED_SQL_WORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "create", "truncate",
            "replace", "merge", "call", "grant", "revoke", "commit", "rollback");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DataHubResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public DataHubQueryResponse resolveReadOnly(DataHubQueryRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        String query = request.query().strip();
        if (!isReadOnlyQuery(query)) {
            throw new IllegalArgumentException("only read-only SELECT queries are allowed");
        }

        Map<String, Object> params = request.params() == null ? Map.of() : request.params();
        var rows = jdbcTemplate.queryForList(query, params);
        return new DataHubQueryResponse(rows, rows.size());
    }

    @Transactional
    public DataHubWriteResponse resolveWrite(DataHubWriteRequest request) {
        if (request == null || request.command() == null || request.command().isBlank()) {
            throw new IllegalArgumentException("command is required");
        }

        String command = request.command().strip();
        if (!isWriteQuery(command)) {
            throw new IllegalArgumentException("only single write statements are allowed");
        }

        Map<String, Object> params = request.params() == null ? Map.of() : request.params();
        int rowsAffected = jdbcTemplate.update(command, params);
        return new DataHubWriteResponse(rowsAffected);
    }

    private boolean isReadOnlyQuery(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select ") || normalized.startsWith("with "))) {
            return false;
        }
        if (MULTI_STATEMENT.matcher(normalized).find()) {
            return false;
        }

        for (String blockedWord : BLOCKED_SQL_WORDS) {
            if (Pattern.compile("\\b" + blockedWord + "\\b").matcher(normalized).find()) {
                return false;
            }
        }

        return true;
    }

    private boolean isWriteQuery(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        if (MULTI_STATEMENT.matcher(normalized).find()) {
            return false;
        }
        return normalized.startsWith("insert ")
                || normalized.startsWith("update ")
                || normalized.startsWith("delete ")
                || normalized.startsWith("replace ");
    }
}
