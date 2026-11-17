package com.novabank.platform.web;

import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/**
 * Adds the request's trace id to an error response, so a customer or support agent can quote it and
 * we find the exact logs and trace. It is only an id: it reveals nothing about the failure.
 */
public final class ProblemTraceId {

  /** The extension member name in the Problem Details body (RFC 9457 allows extensions). */
  public static final String PROPERTY = "traceId";

  /** MDC key that Micrometer Tracing fills for the current span. */
  static final String MDC_KEY = "traceId";

  private ProblemTraceId() {}

  /** Adds {@value #PROPERTY} when a trace is active; leaves the problem unchanged otherwise. */
  public static ProblemDetail addTo(ProblemDetail problem) {
    String traceId = MDC.get(MDC_KEY);
    if (traceId != null && !traceId.isBlank()) {
      problem.setProperty(PROPERTY, traceId);
    }
    return problem;
  }
}
