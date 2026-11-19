package com.novabank.auth.infrastructure.configuration;

import com.novabank.auth.application.security.BootstrapAdmin;
import com.novabank.auth.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code security.bootstrap-admin.email} ({@code BOOTSTRAP_ADMIN_EMAIL}): the first administrator
 * (2.10.1b). Empty disables it.
 */
@Configuration(proxyBeanMethods = false)
class BootstrapAdminConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminConfiguration.class);

  @Bean
  BootstrapAdmin bootstrapAdmin(@Value("${security.bootstrap-admin.email:}") String email) {
    return BootstrapAdmin.of(email);
  }

  @Bean
  BootstrapAdminOnStartup bootstrapAdminOnStartup(
      BootstrapAdmin bootstrapAdmin, UserRepository users, TransactionTemplate transactions) {
    return new BootstrapAdminOnStartup(bootstrapAdmin, users, transactions);
  }

  /** Covers a user who verified their email before the setting was added. */
  static class BootstrapAdminOnStartup {

    private final BootstrapAdmin bootstrapAdmin;
    private final UserRepository users;
    private final TransactionTemplate transactions;

    BootstrapAdminOnStartup(
        BootstrapAdmin bootstrapAdmin, UserRepository users, TransactionTemplate transactions) {
      this.bootstrapAdmin = bootstrapAdmin;
      this.users = users;
      this.transactions = transactions;
    }

    @EventListener(ApplicationReadyEvent.class)
    void grantIfPresent() {
      bootstrapAdmin
          .email()
          .ifPresent(email ->
              transactions.executeWithoutResult(status ->
                  users
                      .findByEmail(email)
                      .filter(bootstrapAdmin::applyTo)
                      .ifPresent(user -> {
                        users.save(user);
                        LOGGER.info("Bootstrap admin: ADMIN granted to user {}", user.id().value());
                      })));
    }
  }
}
