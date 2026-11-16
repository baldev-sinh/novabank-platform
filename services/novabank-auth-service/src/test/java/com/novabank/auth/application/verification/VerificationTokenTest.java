package com.novabank.auth.application.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VerificationTokenTest {

  @Test
  @DisplayName("Tokens are 256-bit, URL-safe and unique; the hash is SHA-256 hex")
  void generatesUrlSafeUniqueTokens() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      VerificationToken token = VerificationToken.generate();
      assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
      assertThat(token.hash()).hasSize(64).matches("[0-9a-f]+");
      assertThat(seen.add(token.value())).isTrue();
    }
  }

  @Test
  @DisplayName("The stored hash matches the hash of the token the user sends back")
  void hashIsDeterministic() {
    VerificationToken token = VerificationToken.generate();

    assertThat(VerificationToken.hashOf(token.value())).isEqualTo(token.hash());
    assertThat(VerificationToken.hashOf("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  @DisplayName("toString never shows the token")
  void toStringIsMasked() {
    VerificationToken token = VerificationToken.generate();

    assertThat(token.toString()).doesNotContain(token.value()).doesNotContain(token.hash());
    assertThat(new VerificationEmailRequested(null, "a@b.io", token.value()).toString())
        .doesNotContain(token.value())
        .doesNotContain("a@b.io");
  }
}
