package com.novabank.account.presentation.rest.account;

import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.response.AccountResponse;
import com.novabank.account.application.usecase.GetAccountsUseCase;
import com.novabank.account.application.usecase.OpenAccountUseCase;
import com.novabank.account.presentation.rest.account.request.OpenAccountRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(value = "/api/v1/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AccountController {

  private final OpenAccountUseCase openAccount;
  private final GetAccountsUseCase getAccounts;

  /**
   * Opens an account. {@code Idempotency-Key} is required: a customer may legitimately open several
   * accounts, so only the key can tell a retry from a second account. A retry with the same key
   * returns the same account (201 again).
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AccountResponse> open(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key")
          @Pattern(
              regexp = "[A-Za-z0-9_-]{8,100}",
              message = "Idempotency-Key must be 8-100 characters of A-Z, a-z, 0-9, _ or -")
          String idempotencyKey,
      @Valid @RequestBody OpenAccountRequest request) {
    AccountResponse account =
        openAccount.open(
            new OpenAccountCommand(
                userId(jwt), idempotencyKey, request.type(), request.currency()));
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.accountId()))
        .body(account);
  }

  @GetMapping
  public List<AccountResponse> listOwn(@AuthenticationPrincipal Jwt jwt) {
    return getAccounts.listOwn(userId(jwt));
  }

  /** 404 for unknown accounts and for other users' accounts alike. */
  @GetMapping("/{accountId}")
  public AccountResponse getOwn(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
    return getAccounts.getOwn(userId(jwt), accountId);
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
