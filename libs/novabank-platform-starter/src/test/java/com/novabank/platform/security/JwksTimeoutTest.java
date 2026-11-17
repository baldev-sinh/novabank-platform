package com.novabank.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** The JWKS call is the only synchronous call between services: it must never hang a request. */
class JwksTimeoutTest {

  @Test
  @DisplayName("A JWKS endpoint that accepts but never answers fails within the read timeout")
  void hangingJwksEndpointTimesOut() throws Exception {
    List<Socket> held = new ArrayList<>();
    try (ServerSocket silent = new ServerSocket(0)) {
      Thread acceptor =
          Thread.ofVirtual()
              .start(() -> {
                try {
                  while (!silent.isClosed()) {
                    held.add(silent.accept()); // never respond
                  }
                } catch (Exception closed) {
                  // test finished
                }
              });

      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, PlatformSecurityAutoConfiguration.class))
          .withPropertyValues(
              "security.jwt.jwk-set-uri=http://localhost:" + silent.getLocalPort() + "/jwks",
              "security.jwt.issuer=http://auth.test",
              "security.jwt.audience=novabank-api",
              "security.jwt.read-timeout=300ms")
          .run(context -> {
            JwtDecoder decoder = context.getBean(JwtDecoder.class);
            long start = System.nanoTime();

            assertThatThrownBy(() -> decoder.decode(signedLookingToken()))
                .isInstanceOf(JwtException.class)
                .hasStackTraceContaining("timed out");

            assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isLessThan(Duration.ofSeconds(5));
          });
      acceptor.interrupt();
    } finally {
      for (Socket socket : held) {
        socket.close();
      }
    }
  }

  /** Well-formed RS256 JWT with a key id, so the decoder has to fetch the JWKS to verify it. */
  private static String signedLookingToken() {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header =
        encoder.encodeToString(
            "{\"alg\":\"RS256\",\"kid\":\"k1\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString("{\"sub\":\"someone\"}".getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + "." + encoder.encodeToString(new byte[256]);
  }
}
