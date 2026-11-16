package com.novabank.payment.infrastructure.messaging;

import com.novabank.payment.application.usecase.SagaUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-sends overdue saga steps (timeouts). Safe on several instances (SKIP LOCKED). */
@Component
@ConditionalOnBooleanProperty(name = "payments.sweeper.enabled", matchIfMissing = true)
public class SagaSweeper {

  private final SagaUseCase saga;

  public SagaSweeper(SagaUseCase saga) {
    this.saga = saga;
  }

  @Scheduled(
      fixedDelayString = "${payments.sweeper.interval-ms:5000}",
      initialDelayString = "${payments.sweeper.interval-ms:5000}")
  public void sweep() {
    saga.resendOverdueSteps();
  }
}
