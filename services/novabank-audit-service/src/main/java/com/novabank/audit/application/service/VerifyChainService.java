package com.novabank.audit.application.service;

import com.novabank.audit.application.port.AuditLog;
import com.novabank.audit.application.port.AuditLog.ChainHead;
import com.novabank.audit.domain.AuditEntry;
import com.novabank.audit.domain.ChainHasher;
import com.novabank.audit.domain.ChainVerification;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Walks the chain up to the head as it was when the check started (later appends are left for the
 * next check). Finds an edited entry (its hash), an inserted or removed one (a gap in seq or a
 * prev_hash that doesn't match) and a removed tail (the last entry isn't the head). Also runs on a
 * schedule; {@code novabank_audit_chain_valid} is 0 when the last check failed, for alerting.
 */
@Service
public class VerifyChainService {

  private static final Logger LOGGER = LoggerFactory.getLogger(VerifyChainService.class);
  private static final int PAGE = 500;

  private final AuditLog auditLog;
  private final ChainHasher hasher;
  private final AtomicLong lastValid = new AtomicLong(1);
  private final AtomicLong lastChecked = new AtomicLong();

  public VerifyChainService(AuditLog auditLog, ChainHasher hasher, MeterRegistry meterRegistry) {
    this.auditLog = auditLog;
    this.hasher = hasher;
    Gauge.builder("novabank.audit.chain.valid", lastValid, AtomicLong::get)
        .description("1 if the last audit chain check passed, 0 if it found tampering")
        .register(meterRegistry);
    Gauge.builder("novabank.audit.chain.checked.entries", lastChecked, AtomicLong::get)
        .description("Entries the last audit chain check walked")
        .register(meterRegistry);
  }

  @Scheduled(
      initialDelayString = "${audit.verify.initial-delay:5m}",
      fixedDelayString = "${audit.verify.interval:1h}")
  public void verifyOnSchedule() {
    ChainVerification result = verify();
    if (result.valid()) {
      LOGGER.info(
          "Audit chain checked: {} entries, head {}", result.checkedEntries(), result.headSeq());
    } else {
      LOGGER.error("Audit chain is broken at entry {}: {}", result.brokenAtSeq(), result.problem());
    }
  }

  public ChainVerification verify() {
    ChainVerification result = walk();
    lastValid.set(result.valid() ? 1 : 0);
    lastChecked.set(result.checkedEntries());
    return result;
  }

  private ChainVerification walk() {
    ChainHead head = auditLog.head();
    String headHash = HexFormat.of().formatHex(head.hash());
    long expectedSeq = 1;
    byte[] previous = ChainHasher.genesis();
    long checked = 0;
    while (expectedSeq <= head.seq()) {
      List<AuditEntry> page = auditLog.chain(expectedSeq - 1, head.seq(), PAGE);
      if (page.isEmpty()) {
        return ChainVerification.broken(
            checked, head.seq(), headHash, expectedSeq, "Entry is missing");
      }
      for (AuditEntry entry : page) {
        if (entry.seq() != expectedSeq) {
          return ChainVerification.broken(
              checked, head.seq(), headHash, expectedSeq, "Entry is missing");
        }
        if (!MessageDigest.isEqual(entry.prevHash(), previous)) {
          return ChainVerification.broken(
              checked, head.seq(), headHash, entry.seq(), "Doesn't follow the entry before it");
        }
        if (!hasher.matches(entry)) {
          return ChainVerification.broken(
              checked, head.seq(), headHash, entry.seq(), "Content doesn't match its hash");
        }
        previous = entry.hash();
        expectedSeq++;
        checked++;
      }
    }
    if (!MessageDigest.isEqual(previous, head.hash())) {
      return ChainVerification.broken(
          checked, head.seq(), headHash, head.seq(), "The last entry isn't the chain head");
    }
    return ChainVerification.valid(checked, head.seq(), headHash);
  }
}
