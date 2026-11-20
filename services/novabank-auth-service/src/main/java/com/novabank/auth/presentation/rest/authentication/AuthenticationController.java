package com.novabank.auth.presentation.rest.authentication;

import com.novabank.auth.application.command.LoginUserCommand;
import com.novabank.auth.application.exception.InvalidRefreshTokenException;
import com.novabank.auth.application.response.CurrentUserResponse;
import com.novabank.auth.application.response.LoginUserResponse;
import com.novabank.auth.application.usecase.GetCurrentUserUseCase;
import com.novabank.auth.application.usecase.LoginUserUseCase;
import com.novabank.auth.application.usecase.SessionUseCase;
import com.novabank.auth.presentation.rest.authentication.request.LoginRequest;
import com.novabank.auth.presentation.rest.authentication.response.CurrentUserApiResponse;
import com.novabank.auth.presentation.rest.authentication.response.LoginApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in and sessions. The access token travels in the response body and the Authorization header;
 * the refresh token only in an HttpOnly cookie (7.1, Decision AE).
 */
@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

  static final String CSRF_HEADER = "X-Requested-With";

  private final LoginUserUseCase loginUserUseCase;
  private final GetCurrentUserUseCase getCurrentUserUserCase;
  private final SessionUseCase sessions;
  private final RefreshCookie refreshCookie;

  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Authenticate user",
      description =
          "Authenticates a user using email and password. The refresh token is set as an HttpOnly"
              + " cookie.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "User authenticated successfully"),
    @ApiResponse(responseCode = "400", description = "Validation failed"),
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
  })
  public ResponseEntity<LoginApiResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginUserResponse response =
        loginUserUseCase.login(new LoginUserCommand(request.email(), request.password()));
    return withSession(response);
  }

  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Refresh the session",
      description =
          "Exchanges the refresh token cookie for a new access token and rotates the cookie."
              + " Requires the X-Requested-With header.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "New access token; cookie rotated"),
    @ApiResponse(responseCode = "401", description = "No usable refresh token: sign in again"),
    @ApiResponse(responseCode = "403", description = "X-Requested-With header missing")
  })
  public ResponseEntity<LoginApiResponse> refresh(
      @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
      HttpServletRequest request) {
    requireCsrfHeader(csrfHeader);
    String token = cookieValue(request).orElseThrow(InvalidRefreshTokenException::new);
    return withSession(sessions.refresh(token));
  }

  @PostMapping("/logout")
  @Operation(
      summary = "Sign out",
      description =
          "Ends the session the refresh token cookie belongs to and clears the cookie."
              + " Idempotent. Requires the X-Requested-With header.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Signed out"),
    @ApiResponse(responseCode = "403", description = "X-Requested-With header missing")
  })
  public ResponseEntity<Void> logout(
      @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader,
      HttpServletRequest request) {
    requireCsrfHeader(csrfHeader);
    cookieValue(request).ifPresent(sessions::logout);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.cleared().toString())
        .build();
  }

  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Get current authenticated user",
      description = "Returns the currently authenticated user's details")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Authenticated user returned successfully"),
    @ApiResponse(responseCode = "401", description = "Authentication required")
  })
  public CurrentUserApiResponse getCurrentUser() {

    CurrentUserResponse response = getCurrentUserUserCase.getCurrentUser();

    return new CurrentUserApiResponse(response.userId(), response.email(), response.roles());
  }

  private ResponseEntity<LoginApiResponse> withSession(LoginUserResponse response) {
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.of(response.refreshToken()).toString())
        .body(
            new LoginApiResponse(
                response.accessToken(), response.tokenType(), response.expiresIn()));
  }

  private static void requireCsrfHeader(String value) {
    if (value == null || value.isBlank()) {
      throw new MissingCsrfHeaderException();
    }
  }

  private Optional<String> cookieValue(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> refreshCookie.name().equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(value -> !value.isBlank())
        .findFirst();
  }
}
