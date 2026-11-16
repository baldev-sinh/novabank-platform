package com.novabank.customer.infrastructure.persistence.event;

import com.novabank.customer.application.port.messaging.ProcessedEventStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcProcessedEventStore implements ProcessedEventStore {

  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  /** ON CONFLICT DO NOTHING: concurrent duplicates can't both win, and nothing throws. */
  @Override
  public boolean markProcessed(UUID eventId, String eventType) {
    int inserted =
        jdbcTemplate.update(
            "INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)"
                + " ON CONFLICT (event_id) DO NOTHING",
            eventId,
            eventType,
            Timestamp.from(clock.instant()));
    return inserted == 1;
  }
}
