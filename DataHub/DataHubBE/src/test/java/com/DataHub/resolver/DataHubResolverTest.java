package com.DataHub.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DataHubResolverTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final DataHubResolver resolver = new DataHubResolver(jdbcTemplate);

    @Test
    void resolvesReadOnlyQueryWithParams() {
        Map<String, Object> params = Map.of("id", 1);
        List<Map<String, Object>> rows = List.of(Map.of("name", "example"));
        when(jdbcTemplate.queryForList("select * from things where id = :id", params)).thenReturn(rows);

        DataHubQueryResponse response = resolver.resolveReadOnly(
                new DataHubQueryRequest(" select * from things where id = :id ", params));

        assertEquals(rows, response.rows());
        assertEquals(1, response.rowCount());
        verify(jdbcTemplate).queryForList("select * from things where id = :id", params);
    }

    @Test
    void usesEmptyParamsWhenParamsAreNull() {
        when(jdbcTemplate.queryForList("select 1", Map.of())).thenReturn(List.of());

        DataHubQueryResponse response = resolver.resolveReadOnly(new DataHubQueryRequest("select 1", null));

        assertEquals(0, response.rowCount());
    }

    @Test
    void rejectsMissingOrMutatingQueries() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveReadOnly(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveReadOnly(new DataHubQueryRequest(" ", null)));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveReadOnly(new DataHubQueryRequest("delete from things", null)));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveReadOnly(new DataHubQueryRequest("select * from things; drop table things", null)));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveReadOnly(new DataHubQueryRequest("with deleted as (delete from things) select * from deleted", null)));
    }

    @Test
    void executesSingleWriteCommand() {
        Map<String, Object> params = Map.of("name", "example");
        when(jdbcTemplate.update("insert into things(name) values (:name)", params)).thenReturn(1);

        DataHubWriteResponse response = resolver.resolveWrite(
                new DataHubWriteRequest(" insert into things(name) values (:name) ", params));

        assertEquals(1, response.rowsAffected());
        verify(jdbcTemplate).update("insert into things(name) values (:name)", params);
    }

    @Test
    void rejectsMissingReadQueriesAndMultiStatementWrites() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveWrite(null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveWrite(new DataHubWriteRequest(" ", null)));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveWrite(new DataHubWriteRequest("select 1", null)));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveWrite(new DataHubWriteRequest("insert into things values (1); delete from things", null)));
    }
}
