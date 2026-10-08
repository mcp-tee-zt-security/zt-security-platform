package com.zt.security.integration;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.flywaydb.core.Flyway;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresMigrationIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Test void allSecurityMigrationsApply() {
    var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var flyway = Flyway.configure().dataSource(ds).load();
    var result = flyway.migrate();
    // 4.75 squashes the original migration chain into one V1 baseline.
    assertTrue(result.migrationsExecuted >= 1);
    assertEquals("1", flyway.info().current().getVersion().getVersion());
    var jdbc = new org.springframework.jdbc.core.JdbcTemplate(ds);
    for (String table : java.util.List.of("tenants", "identities", "policies", "agent_runtime_sessions")) {
      assertNotNull(jdbc.queryForObject("select to_regclass(?)::text", String.class, table), table);
    }
  }
}
