package com.novabank.auth.presentation.rest.exception;

import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.domain.exception.DomainException;
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
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 Problem Details responses.
 *
 * <p>Spring MVC's own exceptions (bad JSON, unsupported media type, unknown path, ...) are handled
 * by {@link ResponseEntityExceptionHandler} and keep their correct 4xx status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(DuplicateEmailException.class)
  ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomainRuleViolation(DomainException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
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
  record FieldViolation(String field, String message) {}
}
