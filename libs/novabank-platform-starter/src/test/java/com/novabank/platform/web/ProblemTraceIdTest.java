package com.novabank.platform.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemTraceIdTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("The active trace id is added to the problem")
  void addsTraceId() {
    MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");

    ProblemDetail problem = ProblemTraceId.addTo(ProblemDetail.forStatus(HttpStatus.NOT_FOUND));

    assertThat(problem.getProperties())
        .containsEntry("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
  }

  @Test
  @DisplayName("Without a trace the problem is left unchanged")
  void noTrace() {
    ProblemDetail problem = ProblemTraceId.addTo(ProblemDetail.forStatus(HttpStatus.NOT_FOUND));

    assertThat(problem.getProperties()).isNullOrEmpty();
  }
}
