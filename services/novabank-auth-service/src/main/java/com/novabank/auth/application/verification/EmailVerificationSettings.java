package com.novabank.auth.application.verification;

import java.time.Duration;
import java.util.Objects;

/**
 * @param tokenTtl how long a verification link stays valid
 * @param resendCooldown minimum time between two verification emails to the same user
 */
public record EmailVerificationSettings(Duration tokenTtl, Duration resendCooldown) {

  public EmailVerificationSettings {
    Objects.requireNonNull(tokenTtl, "tokenTtl cannot be null");
    Objects.requireNonNull(resendCooldown, "resendCooldown cannot be null");
  }
}
