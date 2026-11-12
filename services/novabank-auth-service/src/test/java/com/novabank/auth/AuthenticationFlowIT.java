package com.novabank.auth;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.novabank.auth.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end through the real filter chain, controllers, services, Flyway schema and database. */
@SpringBootTest(
    properties = {
      "security.jwt.issuer=http://auth.test",
      "security.jwt.generate-ephemeral-key=true"
    })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationFlowIT {

  private static final String PASSWORD = "correct horse battery staple";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Register, log in, and call /me with the issued token")
  void registerLoginAndCallMe() throws Exception {
    String email = "flow@example.com";

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email));

    String token = login(email, PASSWORD);

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
  }

  @Test
  @DisplayName("Registering the same email twice returns 409")
  void duplicateRegistrationReturns409() throws Exception {
    register("dup@example.com");

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("dup@example.com", PASSWORD)))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  @DisplayName("A weak password is rejected with 400")
  void weakPasswordReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("weak@example.com", "Password@123")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
  }

  @Test
  @DisplayName("Wrong password and unknown email get the same 401")
  void badCredentialsReturn401() throws Exception {
    register("known@example.com");

    for (String body :
        new String[] {
          credentials("known@example.com", "wrong password value"),
          credentials("nobody@example.com", PASSWORD)
        }) {
      mockMvc
          .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }
  }

  @Test
  @DisplayName("Protected endpoints reject missing and tampered tokens with 401")
  void protectedEndpointRejectsBadTokens() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

    register("tamper@example.com");
    String token = login("tamper@example.com", PASSWORD);
    String tampered = token.substring(0, token.length() - 4) + "AAAA";

    mockMvc
        .perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("The JWKS endpoint is public and exposes only the public key")
  void jwksIsPublicAndHasNoPrivateKey() throws Exception {
    mockMvc
        .perform(get("/.well-known/jwks.json"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
        .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
        .andExpect(jsonPath("$.keys[0]", not(hasKey("d"))));
  }

  private void register(String email) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email, PASSWORD)))
        .andExpect(status().isCreated());
  }

  private String login(String email, String password) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accessToken");
  }

  private static String credentials(String email, String password) {
    return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
  }
}
