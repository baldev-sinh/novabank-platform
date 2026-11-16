package com.novabank.account.application.settings;

import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/**
 * @param supportedCurrencies currencies the bank offers accounts in
 */
public record AccountSettings(Set<Currency> supportedCurrencies) {

  public AccountSettings {
    supportedCurrencies =
        Set.copyOf(
            Objects.requireNonNull(supportedCurrencies, "supportedCurrencies cannot be null"));
  }
}
