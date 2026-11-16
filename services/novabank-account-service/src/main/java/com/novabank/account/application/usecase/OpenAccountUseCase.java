package com.novabank.account.application.usecase;

import com.novabank.account.application.command.OpenAccountCommand;
import com.novabank.account.application.response.AccountResponse;

public interface OpenAccountUseCase {

  /**
   * Opens an account for a KYC-verified customer. The same Idempotency-Key returns the same
   * account, so a retry never opens a second one.
   */
  AccountResponse open(OpenAccountCommand command);
}
