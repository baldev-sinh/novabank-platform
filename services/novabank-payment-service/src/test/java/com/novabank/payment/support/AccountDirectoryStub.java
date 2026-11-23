package com.novabank.payment.support;

import com.novabank.payment.application.exception.AccountServiceUnavailableException;
import com.novabank.payment.application.port.AccountDirectory;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands in for the account service in the payment ITs: every account is ACTIVE unless a test says
 * otherwise. The real HTTP client is covered by HttpAccountDirectoryTest.
 */
public class AccountDirectoryStub implements AccountDirectory {

  private final Map<UUID, String> statuses = new ConcurrentHashMap<>();
  private volatile boolean unavailable;

  public void status(UUID accountId, String status) {
    statuses.put(accountId, status);
  }

  public void unavailable(boolean unavailable) {
    this.unavailable = unavailable;
  }

  @Override
  public Optional<AccountSnapshot> find(UUID accountId) {
    if (unavailable) {
      throw new AccountServiceUnavailableException("stubbed outage", null);
    }
    String status = statuses.getOrDefault(accountId, "ACTIVE");
    return "MISSING".equals(status)
        ? Optional.empty()
        : Optional.of(new AccountSnapshot(accountId, status, "INR"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  public static class Config {

    @Bean
    @Primary
    AccountDirectoryStub accountDirectoryStub() {
      return new AccountDirectoryStub();
    }
  }
}
