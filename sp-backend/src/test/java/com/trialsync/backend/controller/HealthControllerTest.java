package com.trialsync.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.trialsync.backend.config.ApplicationError;
import com.trialsync.backend.dto.common.HealthResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

    private JdbcTemplate jdbcTemplate;
    private HealthController controller;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        controller = new HealthController(jdbcTemplate);
    }

    @Test
    void testLiveReturnsOk() {
        HealthResponse response = controller.live();
        assertEquals("ok", response.status());
    }

    @Test
    void testReadyReturnsReadyWhenMigrationMatches() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("20260802.0014"));

        HealthResponse response = controller.ready();
        assertEquals("ready", response.status());
    }

    @Test
    void testReadyFailsWhenDatabaseUnavailable() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class))
                .thenThrow(new DataAccessException("Connection failed") {});

        ApplicationError error = assertThrows(ApplicationError.class, () -> controller.ready());
        assertEquals("SERVICE_NOT_READY", error.getCode());
        assertEquals(503, error.getStatusCode());
    }

    @Test
    void testReadyFailsWhenMigrationVersionMismatches() {
        when(jdbcTemplate.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), eq(String.class))).thenReturn(List.of("20260802.0013"));

        ApplicationError error = assertThrows(ApplicationError.class, () -> controller.ready());
        assertEquals("DATABASE_MIGRATION_REQUIRED", error.getCode());
        assertEquals(503, error.getStatusCode());
    }
}
