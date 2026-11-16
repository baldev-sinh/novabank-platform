package com.novabank.auth.presentation.rest.user;

import com.novabank.auth.application.usecase.ResendVerificationEmailUseCase;
import com.novabank.auth.application.usecase.VerifyEmailUseCase;
import com.novabank.auth.presentation.rest.user.request.ResendVerificationRequest;
import com.novabank.auth.presentation.rest.user.request.VerifyEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST, not a GET link: mail scanners and link previews open GET links automatically, which would
 * use up the one-time token before the user clicks it. The email links to a frontend page that
 * posts the token here.
 */
@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users/verify-email")
@RequiredArgsConstructor
public class EmailVerificationController {

  private final VerifyEmailUseCase verifyEmail;
  private final ResendVerificationEmailUseCase resendVerificationEmail;

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Verify an email address", description = "Activates the account.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Email verified, the user can sign in"),
    @ApiResponse(responseCode = "400", description = "Token invalid, used or expired")
  })
  public void verify(@Valid @RequestBody VerifyEmailRequest request) {
    verifyEmail.verify(request.token());
  }

  @PostMapping(value = "/resend", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "Resend the verification email",
      description = "Always 202, whether or not the address is registered.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "Accepted"),
    @ApiResponse(responseCode = "400", description = "Validation failed")
  })
  public void resend(@Valid @RequestBody ResendVerificationRequest request) {
    resendVerificationEmail.resend(request.email());
  }
}
