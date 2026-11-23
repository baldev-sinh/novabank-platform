package com.novabank.audit.presentation.rest;

import com.novabank.audit.application.port.AuditLog;
import com.novabank.audit.application.port.AuditLog.AuditQuery;
import com.novabank.audit.application.service.VerifyChainService;
import com.novabank.audit.domain.AuditEntry;
import com.novabank.audit.domain.ChainVerification;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.StringNode;

/**
 * Read-only: search the log and check its integrity (7.4). The parameter constraints use Spring
 * MVC's built-in method validation (no class-level {@code @Validated}): a violation is a {@code
 * HandlerMethodValidationException}, which the platform handler answers with a 400 problem.
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

  private final AuditLog auditLog;
  private final VerifyChainService verifyChain;
  private final JsonMapper jsonMapper;

  public AuditController(AuditLog auditLog, VerifyChainService verifyChain, JsonMapper jsonMapper) {
    this.auditLog = auditLog;
    this.verifyChain = verifyChain;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Newest first. {@code key} is the aggregate id: a userId, customerId, accountId or paymentId.
   * Pass the previous page's {@code nextBefore} as {@code before} for the next page.
   */
  @GetMapping("/entries")
  AuditPage entries(
      @RequestParam(required = false) @Size(max = 200) String key,
      @RequestParam(required = false) @Size(max = 100) String eventType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) @Min(1) Long before,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
    List<AuditEntry> found =
        auditLog.search(new AuditQuery(key, eventType, from, to, before, limit));
    Long nextBefore = found.size() == limit ? found.getLast().seq() : null;
    return new AuditPage(found.stream().map(this::response).toList(), nextBefore);
  }

  /** Walks the whole chain; may take a while on a large log. */
  @GetMapping("/verify")
  ChainVerification verify() {
    return verifyChain.verify();
  }

  private AuditEntryResponse response(AuditEntry entry) {
    return new AuditEntryResponse(
        entry.seq(),
        entry.eventId(),
        entry.eventType(),
        entry.topic(),
        entry.recordKey(),
        entry.occurredAt(),
        entry.recordedAt(),
        payload(entry.payload()),
        HexFormat.of().formatHex(entry.hash()));
  }

  /** As JSON when it is JSON; otherwise as the text that was received. */
  private JsonNode payload(String payload) {
    try {
      return jsonMapper.readTree(payload);
    } catch (JacksonException ex) {
      return StringNode.valueOf(payload);
    }
  }

  record AuditPage(List<AuditEntryResponse> entries, Long nextBefore) {}

  record AuditEntryResponse(
      long seq,
      UUID eventId,
      String eventType,
      String topic,
      String key,
      Instant occurredAt,
      Instant recordedAt,
      JsonNode payload,
      String hash) {}
}
