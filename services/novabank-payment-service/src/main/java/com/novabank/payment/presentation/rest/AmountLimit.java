package com.novabank.payment.presentation.rest;

import com.novabank.payment.domain.exception.DomainException;
import com.novabank.payment.infrastructure.configuration.PaymentProperties;
import com.novabank.platform.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Parses the amount and applies the per-payment maximum. */
@Component
@RequiredArgsConstructor
class AmountLimit {

  private final PaymentProperties properties;

  Money parse(String amount, String currency) {
    Money money = Money.of(amount, currency);
    if (money.amount().compareTo(properties.maxAmount()) > 0) {
      throw new DomainException(
          "Amount is above the limit of " + properties.maxAmount().toPlainString() + ".");
    }
    return money;
  }
}
