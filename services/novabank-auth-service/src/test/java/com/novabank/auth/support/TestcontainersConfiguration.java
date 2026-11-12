package com.novabank.auth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL for integration tests, same image as compose.yaml. {@code @ServiceConnection}
 * points the datasource at it, so no URL or credentials are needed in test config. Spring caches
 * the context, so one container is shared by all tests that import this.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18.6-trixie");
  }
}
