package com.novabank.auth.presentation.rest.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novabank.auth.application.exception.DuplicateEmailException;
import com.novabank.auth.application.exception.InvalidCredentialsException;
import com.novabank.auth.domain.exception.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("Duplicate email returns 409 Problem Details")
  void duplicateEmailReturns409() throws Exception {
    mockMvc
        .perform(get("/test/duplicate-email"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.title").value("Conflict"))
        .andExpect(jsonPath("$.detail").value("Email already registered"))
        .andExpect(jsonPath("$.instance").value("/test/duplicate-email"));
  }

  @Test
  @DisplayName("Invalid credentials return a generic 401 message")
  void invalidCredentialsReturns401() throws Exception {
    mockMvc
        .perform(get("/test/invalid-credentials"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Invalid email or password."));
  }

  @Test
  @DisplayName("Domain rule violations return 400, not 500")
  void domainExceptionReturns400() throws Exception {
    mockMvc
        .perform(get("/test/domain"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Email address format is invalid"));
  }

  @Test
  @DisplayName("Bean validation errors return 400 with the invalid fields listed")
  void validationErrorsReturn400WithFields() throws Exception {
    mockMvc
        .perform(post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Request validation failed."))
        .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name")))
        .andExpect(jsonPath("$.errors[0].message").value("Name is required"));
  }

  @Test
  @DisplayName("Malformed JSON returns 400, not 500")
  void malformedJsonReturns400() throws Exception {
    mockMvc
        .perform(
            post("/test/validated").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  @DisplayName("Unsupported media type returns 415, not 500")
  void unsupportedMediaTypeReturns415() throws Exception {
    mockMvc
        .perform(post("/test/validated").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  @DisplayName("Unexpected exceptions return a generic 500 without internal details")
  void unexpectedExceptionReturnsGeneric500() throws Exception {
    mockMvc
        .perform(get("/test/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
        .andExpect(content().string(not(containsString("secret-internal-detail"))));
  }

  @Test
  @DisplayName("Security exceptions are rethrown for Spring Security to handle")
  void securityExceptionsAreRethrown() {
    assertThatThrownBy(() -> mockMvc.perform(get("/test/access-denied")))
        .hasRootCauseInstanceOf(AccessDeniedException.class);
  }

  @RestController
  static class TestController {

    @GetMapping("/test/duplicate-email")
    void duplicateEmail() {
      throw new DuplicateEmailException("Email already registered");
    }

    @GetMapping("/test/invalid-credentials")
    void invalidCredentials() {
      throw new InvalidCredentialsException("user not found: someone@example.com");
    }

    @GetMapping("/test/domain")
    void domain() {
      throw new DomainException("Email address format is invalid");
    }

    @PostMapping("/test/validated")
    void validated(@Valid @RequestBody TestRequest request) {}

    @GetMapping("/test/unexpected")
    void unexpected() {
      throw new IllegalStateException("secret-internal-detail");
    }

    @GetMapping("/test/access-denied")
    void accessDenied() {
      throw new AccessDeniedException("denied");
    }
  }

  record TestRequest(@NotBlank(message = "Name is required") String name) {}
}
