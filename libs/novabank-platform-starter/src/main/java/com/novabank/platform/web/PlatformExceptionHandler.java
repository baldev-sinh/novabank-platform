package com.novabank.platform.web;

import com.novabank.platform.idempotency.IdempotencyKeyReusedException;
import com.novabank.platform.money.InvalidMoneyException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Base for each service's {@code @RestControllerAdvice}: maps exceptions to RFC 9457 Problem
 * Details. Services extend it and add handlers for their own exceptions.
 *
 * <p>Spring MVC's own exceptions (bad JSON, unsupported media type, unknown path, ...) are handled
 * by {@link ResponseEntityExceptionHandler} and keep their correct 4xx status.
 */
public abstract class PlatformExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(PlatformExceptionHandler.class);

  @ExceptionHandler(InvalidMoneyException.class)
  ProblemDetail handleInvalidMoney(InvalidMoneyException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  /** Same key, different request: the client has a bug, retrying won't help (RFC 9110 422). */
  @ExceptionHandler(IdempotencyKeyReusedException.class)
  ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
  }

  /**
   * Lets Spring Security turn these into 401/403 via its entry point and access denied handler,
   * instead of the catch-all below turning them into 500.
   */
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  void rethrowSecurityException(RuntimeException ex) {
    throw ex;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    LOGGER.error(
        "Unhandled exception while processing [{} {}]",
        request.getMethod(),
        request.getRequestURI(),
        ex);
    // Never expose internal details (stack traces, SQL, class names) to the client.
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    List<FieldViolation> violations =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();

    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed.");
    problem.setProperty("errors", violations);

    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  /** One invalid request field, listed in the {@code errors} member of a 400 response. */
  public record FieldViolation(String field, String message) {}
}
