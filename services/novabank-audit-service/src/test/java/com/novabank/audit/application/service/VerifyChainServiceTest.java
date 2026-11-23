package com.novabank.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.novabank.audit.application.port.AuditLog;
import com.novabank.audit.domain.AuditEntry;
import com.novabank.audit.domain.ChainHasher;
import com.novabank.audit.domain.ChainVerification;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Every way the chain can be broken, against an in-memory log. */
class VerifyChainServiceTest {

  private static final Instant AT = Instant.parse("2026-11-23T10:00:00Z");

  private final ChainHasher hasher = new ChainHasher(new byte[32]);
  private final InMemoryLog log = new InMemoryLog();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private VerifyChainService service;

  @BeforeEach
  void setUp() {
    service = new VerifyChainService(log, hasher, meters);
    for (int i = 0; i < 3; i++) {
      append("{\"n\":" + i + "}");
    }
  }

  @Test
  @DisplayName("An intact chain verifies and the gauge reads 1")
  void intact() {
    ChainVerification result = service.verify();

    assertThat(result.valid()).isTrue();
    assertThat(result.checkedEntries()).isEqualTo(3);
    assertThat(result.headSeq()).isEqualTo(3);
    assertThat(meters.get("novabank.audit.chain.valid").gauge().value()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("An empty log is valid")
  void empty() {
    VerifyChainService fresh =
        new VerifyChainService(new InMemoryLog(), hasher, new SimpleMeterRegistry());

    assertThat(fresh.verify().valid()).isTrue();
  }

  @Test
  @DisplayName("A removed entry in the middle is a gap")
  void gap() {
    log.entries.remove(1);

    ChainVerification result = service.verify();

    assertThat(result.valid()).isFalse();
    assertThat(result.brokenAtSeq()).isEqualTo(2);
    assertThat(result.problem()).isEqualTo("Entry is missing");
    assertThat(meters.get("novabank.audit.chain.valid").gauge().value()).isZero();
  }

  @Test
  @DisplayName("A removed tail is missing up to the head")
  void removedTail() {
    log.entries.remove(2);

    assertThat(service.verify().brokenAtSeq()).isEqualTo(3);
  }

  @Test
  @DisplayName("An entry re-hashed onto the wrong predecessor doesn't follow it")
  void wrongPredecessor() {
    AuditEntry second = log.entries.get(1);
    byte[] forgedPrevious = new byte[32];
    forgedPrevious[0] = 7;
    log.entries.set(1, withHash(second, forgedPrevious, second.hash()));

    ChainVerification result = service.verify();

    assertThat(result.brokenAtSeq()).isEqualTo(2);
    assertThat(result.problem()).isEqualTo("Doesn't follow the entry before it");
  }

  @Test
  @DisplayName("A head that points past the last real entry is found")
  void headMismatch() {
    log.headHash = new byte[32];

    ChainVerification result = service.verify();

    assertThat(result.valid()).isFalse();
    assertThat(result.problem()).isEqualTo("The last entry isn't the chain head");
  }

  @Test
  @DisplayName("The scheduled check runs the same verification")
  void scheduled() {
    log.entries.remove(1);

    service.verifyOnSchedule();

    assertThat(meters.get("novabank.audit.chain.valid").gauge().value()).isZero();
  }

  private void append(String payload) {
    long seq = log.entries.size() + 1L;
    byte[] previous = log.headHash;
    UUID id = UUID.randomUUID();
    byte[] hash = hasher.hash(previous, seq, id, "T.v1", "topic", "k", AT, AT, payload);
    log.entries.add(new AuditEntry(seq, id, "T.v1", "topic", "k", AT, AT, payload, previous, hash));
    log.headSeq = seq;
    log.headHash = hash;
  }

  private static AuditEntry withHash(AuditEntry e, byte[] previous, byte[] hash) {
    return new AuditEntry(
        e.seq(),
        e.eventId(),
        e.eventType(),
        e.topic(),
        e.recordKey(),
        e.occurredAt(),
        e.recordedAt(),
        e.payload(),
        previous,
        hash);
  }

  private static final class InMemoryLog implements AuditLog {
    private final List<AuditEntry> entries = new ArrayList<>();
    private long headSeq;
    private byte[] headHash = ChainHasher.genesis();

    @Override
    public ChainHead lockHead() {
      return head();
    }

    @Override
    public ChainHead head() {
      return new ChainHead(headSeq, headHash);
    }

    @Override
    public boolean contains(UUID eventId) {
      return entries.stream().anyMatch(e -> e.eventId().equals(eventId));
    }

    @Override
    public void append(AuditEntry entry) {
      entries.add(entry);
    }

    @Override
    public List<AuditEntry> chain(long afterSeq, long untilSeq, int limit) {
      return entries.stream()
          .filter(e -> e.seq() > afterSeq && e.seq() <= untilSeq)
          .limit(limit)
          .toList();
    }

    @Override
    public List<AuditEntry> search(AuditQuery query) {
      return List.of();
    }
  }
}
