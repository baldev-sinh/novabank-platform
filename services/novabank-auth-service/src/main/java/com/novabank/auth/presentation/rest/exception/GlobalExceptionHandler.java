package com.novabank.auth.presentation.rest.exception;

import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.exception.EmailNotVerifiedException;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.application.exception.InvalidVerificationTokenException;
import com.novabank.auth.domain.exception.DomainException;
import com.novabank.platform.web.PlatformExceptionHandler;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Auth-specific RFC 9457 mappings. Validation errors, security exceptions and the generic 500 come
 * from {@link PlatformExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends PlatformExceptionHandler {

  /** Stable identifier clients can match on, instead of parsing the message. */
  static final URI EMAIL_NOT_VERIFIED_TYPE = URI.create("urn:novabank:problem:email-not-verified");

  @ExceptionHandler(DuplicateEmailException.class)
  ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
  }

  /** Only reached after a correct password; lets the client offer to resend the email. */
  @ExceptionHandler(EmailNotVerifiedException.class)
  ProblemDetail handleEmailNotVerified(EmailNotVerifiedException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problem.setType(EMAIL_NOT_VERIFIED_TYPE);
    return problem;
  }

  @ExceptionHandler(InvalidVerificationTokenException.class)
  ProblemDetail handleInvalidVerificationToken(InvalidVerificationTokenException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(DomainException.class)
  ProblemDetail handleDomainRuleViolation(DomainException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
