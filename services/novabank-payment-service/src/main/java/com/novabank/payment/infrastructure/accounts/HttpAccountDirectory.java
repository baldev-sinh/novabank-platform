package com.novabank.payment.infrastructure.accounts;

import com.novabank.payment.application.exception.AccountServiceUnavailableException;
import com.novabank.payment.application.port.AccountDirectory;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The account service's internal status endpoint, called with a service token (7.3). Timeouts and a
 * circuit breaker (Decision AG) keep a slow or failing account service from tying up deposits: once
 * it fails repeatedly, calls fail fast until it recovers. A 404 is an answer, not a failure.
 */
public class HttpAccountDirectory implements AccountDirectory {

  static final String CLIENT_REGISTRATION = "account-service";

  private final RestClient restClient;
  private final CircuitBreaker circuitBreaker;

  public HttpAccountDirectory(RestClient restClient, CircuitBreaker circuitBreaker) {
    this.restClient = restClient;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public Optional<AccountSnapshot> find(UUID accountId) {
    try {
      return circuitBreaker.executeSupplier(() -> fetch(accountId));
    } catch (CallNotPermittedException ex) {
      throw new AccountServiceUnavailableException("Account service circuit is open", ex);
    } catch (RestClientException ex) {
      throw new AccountServiceUnavailableException("Account service call failed", ex);
    } catch (OAuth2AuthorizationException ex) {
      // No service token: auth is unreachable or refused this service.
      throw new AccountServiceUnavailableException("No service token for the account service", ex);
    }
  }

  private Optional<AccountSnapshot> fetch(UUID accountId) {
    try {
      return Optional.ofNullable(
          restClient
              .get()
              .uri("/api/v1/internal/accounts/{id}", accountId)
              .attributes(
                  RequestAttributeClientRegistrationIdResolver.clientRegistrationId(
                      CLIENT_REGISTRATION))
              .retrieve()
              .body(AccountSnapshot.class));
    } catch (HttpClientErrorException ex) {
      if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
        return Optional.empty();
      }
      throw ex;
    }
  }
}
