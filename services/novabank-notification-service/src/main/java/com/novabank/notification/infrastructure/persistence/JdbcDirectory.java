package com.novabank.notification.infrastructure.persistence;

import com.novabank.notification.application.port.Directory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDirectory implements Directory {

  private final JdbcClient jdbc;

  public JdbcDirectory(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void saveUser(UUID userId, String email, Instant registeredAt) {
    jdbc.sql(
            """
            INSERT INTO known_users (user_id, email, registered_at)
            VALUES (:userId, :email, :registeredAt)
            ON CONFLICT (user_id) DO UPDATE SET email = EXCLUDED.email
            """)
        .param("userId", userId)
        .param("email", email)
        .param("registeredAt", Timestamp.from(registeredAt))
        .update();
  }

  @Override
  public void saveAccount(UUID accountId, String accountNumber, UUID ownerUserId, String currency) {
    jdbc.sql(
            """
            INSERT INTO known_accounts (account_id, account_number, owner_user_id, currency)
            VALUES (:accountId, :accountNumber, :ownerUserId, :currency)
            ON CONFLICT (account_id) DO NOTHING
            """)
        .param("accountId", accountId)
        .param("accountNumber", accountNumber)
        .param("ownerUserId", ownerUserId)
        .param("currency", currency)
        .update();
  }

  @Override
  public Optional<String> emailOf(UUID userId) {
    return jdbc.sql("SELECT email FROM known_users WHERE user_id = :userId")
        .param("userId", userId)
        .query(String.class)
        .optional();
  }

  @Override
  public Optional<KnownAccount> account(UUID accountId) {
    return jdbc.sql(
            "SELECT account_id, account_number, owner_user_id, currency FROM known_accounts"
                + " WHERE account_id = :accountId")
        .param("accountId", accountId)
        .query((rs, row) ->
            new KnownAccount(
                rs.getObject("account_id", UUID.class),
                rs.getString("account_number"),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("currency")))
        .optional();
  }
}
