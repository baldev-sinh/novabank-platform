package com.novabank.platform.support;

import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One real PostgreSQL per test JVM, same image as compose.yaml, with the tables the starter's
 * javadoc tells services to create. The components are tested without a Spring context, so a {@link
 * TransactionTemplate} provides the transactions their callers would.
 */
public final class PostgresTestDatabase {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.7-trixie");

  private static DataSource dataSource;

  private PostgresTestDatabase() {}

  public static synchronized DataSource dataSource() {
    if (dataSource == null) {
      POSTGRES.start();
      dataSource =
          new DriverManagerDataSource(
              POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
      createSchema(JdbcClient.create(dataSource));
    }
    return dataSource;
  }

  public static JdbcClient jdbcClient() {
    return JdbcClient.create(dataSource());
  }

  public static TransactionTemplate transactions() {
    return new TransactionTemplate(new DataSourceTransactionManager(dataSource()));
  }

  private static void createSchema(JdbcClient jdbc) {
    jdbc.sql(
            """
            CREATE TABLE outbox_events (
                id             UUID         PRIMARY KEY,
                position       BIGINT       GENERATED ALWAYS AS IDENTITY,
                topic          VARCHAR(255) NOT NULL,
                aggregate_type VARCHAR(50)  NOT NULL,
                aggregate_id   UUID         NOT NULL,
                event_type     VARCHAR(100) NOT NULL,
                payload        JSONB        NOT NULL,
                created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
                published_at   TIMESTAMP WITH TIME ZONE,
                trace_headers  JSONB
            )
            """)
        .update();
    jdbc.sql(
            """
            CREATE TABLE idempotency_keys (
                owner_id        UUID         NOT NULL,
                idempotency_key VARCHAR(100) NOT NULL,
                request_hash    VARCHAR(64)  NOT NULL,
                response_body   JSONB,
                created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
                PRIMARY KEY (owner_id, idempotency_key)
            )
            """)
        .update();
    jdbc.sql(
            """
            CREATE TABLE processed_events (
                event_id     UUID         PRIMARY KEY,
                event_type   VARCHAR(100) NOT NULL,
                processed_at TIMESTAMP WITH TIME ZONE NOT NULL
            )
            """)
        .update();
  }
}
