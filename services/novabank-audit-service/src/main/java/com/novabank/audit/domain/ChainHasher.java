package com.novabank.audit.domain;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@code hash = HMAC-SHA256(key, prevHash ‖ seq ‖ eventId ‖ type ‖ topic ‖ key ‖ occurredAt ‖
 * recordedAt ‖ payload)} (Decision AH). Each field is length-prefixed, so no two different entries
 * encode to the same bytes. Timestamps are hashed at millisecond precision, which is what Kafka
 * gives and what PostgreSQL stores exactly.
 */
public final class ChainHasher {

  public static final int HASH_LENGTH = 32;

  private static final byte[] GENESIS = new byte[HASH_LENGTH];
  private static final String ALGORITHM = "HmacSHA256";
  private static final int MIN_KEY_LENGTH = 32;

  private final SecretKeySpec key;

  public ChainHasher(byte[] key) {
    Objects.requireNonNull(key, "key cannot be null");
    if (key.length < MIN_KEY_LENGTH) {
      // RFC 2104 §3: a key shorter than the hash output weakens the MAC.
      throw new IllegalArgumentException(
          "The audit chain key must be at least " + MIN_KEY_LENGTH + " bytes");
    }
    this.key = new SecretKeySpec(key.clone(), ALGORITHM);
  }

  /** The prev_hash of the first entry: 32 zero bytes. */
  public static byte[] genesis() {
    return GENESIS.clone();
  }

  public byte[] hash(
      byte[] prevHash,
      long seq,
      UUID eventId,
      String eventType,
      String topic,
      String recordKey,
      Instant occurredAt,
      Instant recordedAt,
      String payload) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(256 + payload.length());
    field(out, prevHash);
    field(out, longBytes(seq));
    field(out, text(eventId.toString()));
    field(out, text(eventType));
    field(out, text(topic));
    field(out, recordKey == null ? null : text(recordKey));
    field(out, longBytes(occurredAt.toEpochMilli()));
    field(out, longBytes(recordedAt.toEpochMilli()));
    field(out, text(payload));
    try {
      // Mac isn't thread-safe; a new instance per entry is cheap next to the database write.
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      return mac.doFinal(out.toByteArray());
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HmacSHA256 is not available", ex);
    }
  }

  /** Recomputes the entry's hash from its fields and compares in constant time. */
  public boolean matches(AuditEntry entry) {
    byte[] expected =
        hash(
            entry.prevHash(),
            entry.seq(),
            entry.eventId(),
            entry.eventType(),
            entry.topic(),
            entry.recordKey(),
            entry.occurredAt(),
            entry.recordedAt(),
            entry.payload());
    return MessageDigest.isEqual(expected, entry.hash());
  }

  private static byte[] text(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] longBytes(long value) {
    return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
  }

  /** -1 for a missing value, otherwise the length, then the bytes. */
  private static void field(ByteArrayOutputStream out, byte[] value) {
    int length = value == null ? -1 : value.length;
    out.writeBytes(ByteBuffer.allocate(Integer.BYTES).putInt(length).array());
    if (value != null) {
      out.writeBytes(value);
    }
  }
}
