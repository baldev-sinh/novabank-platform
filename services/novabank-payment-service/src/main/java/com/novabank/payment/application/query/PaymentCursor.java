package com.novabank.payment.application.query;

import com.novabank.payment.domain.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/**
 * Where a page of payments ends: the last payment's creation time and id (the id breaks ties
 * between payments created in the same microsecond). Clients get it as an opaque string.
 */
public record PaymentCursor(Instant createdAt, UUID paymentId) {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  public String encode() {
    return ENCODER.encodeToString((createdAt + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @throws DomainException (400) for anything that isn't a cursor this service made
   */
  public static PaymentCursor decode(String value) {
    try {
      String[] parts = new String(DECODER.decode(value), StandardCharsets.UTF_8).split("\\|", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("two parts expected");
      }
      return new PaymentCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
    } catch (IllegalArgumentException | DateTimeParseException ex) {
      throw new DomainException("The page cursor is not valid.");
    }
  }
}
