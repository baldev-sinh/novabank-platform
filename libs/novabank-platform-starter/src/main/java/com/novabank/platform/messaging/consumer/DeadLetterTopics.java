package com.novabank.platform.messaging.consumer;

import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * Dead-letter topic names: one per source topic <em>and</em> consumer group, {@code
 * <topic>.<group>-dlt}. Several services read the same topic (ledger events: account, payment,
 * audit, notification); with a shared {@code <topic>-dlt} a replay would feed one service's
 * failures to all of them. Each service creates the dead-letter topics of what it consumes.
 */
public final class DeadLetterTopics {

  private DeadLetterTopics() {}

  public static String of(String topic, String consumerGroup) {
    Objects.requireNonNull(topic, "topic cannot be null");
    if (!StringUtils.hasText(consumerGroup)) {
      throw new IllegalArgumentException(
          "A consumer group is needed to name the dead-letter topic");
    }
    return topic + "." + consumerGroup + "-dlt";
  }
}
