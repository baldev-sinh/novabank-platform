package com.novabank.account.presentation.rest.account;

import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.usecase.GetAccountsUseCase;
import com.novabank.account.domain.model.AccountStatus;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For other NovaBank services, authenticated with a service token carrying {@code accounts:read}
 * (SecurityConfiguration). The payment service asks whether an account can receive money before
 * starting a deposit (7.3). Only what that needs: no number, owner or balance.
 */
@RestController
@RequestMapping(value = "/api/v1/internal/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class InternalAccountController {

  private final GetAccountsUseCase accounts;

  /** 404 Problem Details if the account doesn't exist. */
  @GetMapping("/{accountId}")
  public AccountStatusResponse status(@PathVariable UUID accountId) {
    AccountResponse account = accounts.get(accountId);
    return new AccountStatusResponse(account.accountId(), account.status(), account.currency());
  }

  public record AccountStatusResponse(UUID accountId, AccountStatus status, String currency) {}
}
