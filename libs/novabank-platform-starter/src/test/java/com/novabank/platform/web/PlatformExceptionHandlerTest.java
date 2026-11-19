package com.novabank.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novabank.platform.idempotency.IdempotencyKeyReusedException;
import com.novabank.platform.money.InvalidMoneyException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The shared RFC 9457 mappings every service's advice inherits. */
class PlatformExceptionHandlerTest {

  @RestControllerAdvice
  static class ServiceAdvice extends PlatformExceptionHandler {}

  record CreateRequest(@NotBlank(message = "Name is required") String name) {}

  @RestController
  static class TestController {

    @GetMapping("/money")
    void money() {
      throw new InvalidMoneyException("Amount has too many decimal places for INR.");
    }

    @GetMapping("/reused-key")
    void reusedKey() {
      throw new IdempotencyKeyReusedException();
    }

    @GetMapping("/denied")
    void denied() {
      throw new AccessDeniedException("no");
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("SELECT * FROM secrets failed at com.novabank.Internal");
    }

    @PostMapping("/create")
    void create(@Valid @RequestBody CreateRequest request) {}
  }

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new TestController())
          .setControllerAdvice(new ServiceAdvice())
          .build();

  @Test
  @DisplayName("Invalid money is a 400 with the reason")
  void invalidMoneyIs400() throws Exception {
    mockMvc
        .perform(get("/money"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Amount has too many decimal places for INR."));
  }

  @Test
  @DisplayName("A reused Idempotency-Key is a 422: retrying the same request won't help")
  void reusedIdempotencyKeyIs422() throws Exception {
    mockMvc
        .perform(get("/reused-key"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.status").value(422));
  }

  @Test
  @DisplayName("Validation errors are a 400 listing each invalid field")
  void validationErrorsListFields() throws Exception {
    mockMvc
        .perform(post("/create").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request validation failed."))
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].message").value("Name is required"));
  }

  @Test
  @DisplayName("Unexpected errors are a generic 500 that reveals nothing internal")
  void unexpectedErrorIsGeneric500() throws Exception {
    String body =
        mockMvc
            .perform(get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).doesNotContain("SELECT", "secrets", "com.novabank");
  }

  @Test
  @DisplayName("Security exceptions are left to Spring Security, not turned into a 500")
  void securityExceptionsAreRethrown() {
    assertThatThrownBy(() -> mockMvc.perform(get("/denied")))
        .hasRootCauseInstanceOf(AccessDeniedException.class);
  }
}
