package com.novabank.customer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.novabank.customer.support.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * End-to-end through security, controller, services, Flyway schema and database. Tokens are
 * simulated with {@code jwt()}; the real issuer/audience/expiry checks are covered by
 * SecurityConfigurationTest.
 */
@SpringBootTest(
    properties = {
      "security.jwt.jwk-set-uri=http://localhost:1/not-used-in-this-test",
      "security.jwt.issuer=http://auth.test",
      "security.jwt.audience=novabank-api"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerRegistrationIT {

  private static final String VALID_BODY =
      "{\"firstName\":\"Baldev\",\"lastName\":\"Parmar\","
          + "\"dateOfBirth\":\"2001-01-15\",\"phone\":\"+91 98765 43210\"}";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("A customer registers once and reads their own profile")
  void registerAndReadOwnProfile() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(register(VALID_BODY).with(customer(userId)))
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/customers/me"))
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.status").value("PENDING_KYC"));

    mockMvc
        .perform(get("/api/v1/customers/me").with(customer(userId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Baldev"))
        .andExpect(jsonPath("$.phone").value("+919876543210"))
        .andExpect(jsonPath("$.dateOfBirth").value("2001-01-15"));
  }

  @Test
  @DisplayName("The user id comes from the token, never from the request body")
  void ignoresUserIdInBody() throws Exception {
    UUID caller = UUID.randomUUID();
    String bodyWithForeignUserId =
        VALID_BODY.replace("{", "{\"userId\":\"" + UUID.randomUUID() + "\",");

    mockMvc
        .perform(register(bodyWithForeignUserId).with(customer(caller)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(caller.toString()));
  }

  @Test
  @DisplayName("A second registration for the same user returns 409")
  void secondRegistrationReturns409() throws Exception {
    UUID userId = UUID.randomUUID();
    mockMvc.perform(register(VALID_BODY).with(customer(userId))).andExpect(status().isCreated());

    mockMvc
        .perform(register(VALID_BODY).with(customer(userId)))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  @DisplayName("Business rules return 400: invalid phone, under 18, missing fields")
  void invalidInputReturns400() throws Exception {
    mockMvc
        .perform(register(VALID_BODY.replace("+91 98765 43210", "12345")).with(customer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("international format")));

    mockMvc
        .perform(register(VALID_BODY.replace("2001-01-15", "2015-01-15")).with(customer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Customer must be at least 18 years old."));

    mockMvc
        .perform(register("{}").with(customer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(4));
  }

  @Test
  @DisplayName("No profile yet returns 404")
  void noProfileReturns404() throws Exception {
    mockMvc.perform(get("/api/v1/customers/me").with(customer())).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("No token returns 401; a token without the CUSTOMER role returns 403")
  void securityRules() throws Exception {
    mockMvc
        .perform(register(VALID_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

    mockMvc
        .perform(
            register(VALID_BODY)
                .with(
                    jwt()
                        .jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  private static MockHttpServletRequestBuilder register(String body) {
    return post("/api/v1/customers").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static RequestPostProcessor customer() {
    return customer(UUID.randomUUID());
  }

  private static RequestPostProcessor customer(UUID userId) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }
}
