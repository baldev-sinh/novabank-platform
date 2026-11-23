package com.novabank.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChainHasherTest {

  private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes();
  private static final UUID EVENT = UUID.fromString("7b0c64f4-1b53-4f2b-9a51-30d7e7a3c001");
  private static final Instant AT = Instant.parse("2026-11-23T10:00:00.123Z");

  private final ChainHasher hasher = new ChainHasher(KEY);

  @Test
  @DisplayName("Same entry, same hash; every field changes it")
  void everyFieldCounts() {
    byte[] base = hash(hasher, ChainHasher.genesis(), 1, "k", "{\"a\":1}");

    assertThat(hash(hasher, ChainHasher.genesis(), 1, "k", "{\"a\":1}")).isEqualTo(base);
    assertThat(hash(hasher, ChainHasher.genesis(), 2, "k", "{\"a\":1}")).isNotEqualTo(base);
    assertThat(hash(hasher, ChainHasher.genesis(), 1, "k2", "{\"a\":1}")).isNotEqualTo(base);
    assertThat(hash(hasher, ChainHasher.genesis(), 1, null, "{\"a\":1}")).isNotEqualTo(base);
    assertThat(hash(hasher, ChainHasher.genesis(), 1, "k", "{\"a\":2}")).isNotEqualTo(base);
    byte[] otherPrevious = ChainHasher.genesis();
    otherPrevious[0] = 1;
    assertThat(hash(hasher, otherPrevious, 1, "k", "{\"a\":1}")).isNotEqualTo(base);
  }

  @Test
  @DisplayName("Fields are length-prefixed: moving bytes between fields changes the hash")
  void fieldBoundariesCount() {
    byte[] a = hasher.hash(ChainHasher.genesis(), 1, EVENT, "Type.v1", "topicX", "k", AT, AT, "p");
    byte[] b = hasher.hash(ChainHasher.genesis(), 1, EVENT, "Type.v1t", "opicX", "k", AT, AT, "p");

    assertThat(a).isNotEqualTo(b);
  }

  @Test
  @DisplayName("Without the key the hash can't be reproduced")
  void keyMatters() {
    byte[] otherKey = Arrays.copyOf(KEY, KEY.length);
    otherKey[0] ^= 1;

    assertThat(hash(new ChainHasher(otherKey), ChainHasher.genesis(), 1, "k", "p"))
        .isNotEqualTo(hash(hasher, ChainHasher.genesis(), 1, "k", "p"));
  }

  @Test
  @DisplayName("matches() detects an edited entry")
  void matchesDetectsEdits() {
    byte[] hash = hash(hasher, ChainHasher.genesis(), 1, "k", "{\"amount\":\"10.00\"}");
    AuditEntry entry =
        new AuditEntry(
            1,
            EVENT,
            "Type.v1",
            "topic",
            "k",
            AT,
            AT,
            "{\"amount\":\"10.00\"}",
            ChainHasher.genesis(),
            hash);
    AuditEntry edited =
        new AuditEntry(
            1,
            EVENT,
            "Type.v1",
            "topic",
            "k",
            AT,
            AT,
            "{\"amount\":\"99.00\"}",
            ChainHasher.genesis(),
            hash);

    assertThat(hasher.matches(entry)).isTrue();
    assertThat(hasher.matches(edited)).isFalse();
  }

  @Test
  @DisplayName("Keys shorter than 32 bytes are refused")
  void shortKeyRefused() {
    assertThatThrownBy(() -> new ChainHasher(new byte[16]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] hash(ChainHasher h, byte[] prev, long seq, String key, String payload) {
    return h.hash(prev, seq, EVENT, "Type.v1", "topic", key, AT, AT, payload);
  }
}
