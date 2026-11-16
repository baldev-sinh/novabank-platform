package com.novabank.payment.application.settings;

import java.time.Duration;
import java.util.Objects;

/**
 * @param stepTimeout how long to wait for a reply before re-sending the step's command
 * @param maxAttempts re-sends per step before the payment is flagged for operations staff
 */
public record SagaSettings(Duration stepTimeout, int maxAttempts) {

  public SagaSettings {
    Objects.requireNonNull(stepTimeout, "stepTimeout cannot be null");
  }
}
