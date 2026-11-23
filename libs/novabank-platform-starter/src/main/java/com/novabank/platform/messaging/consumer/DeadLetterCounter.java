package com.novabank.platform.messaging.consumer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * {@code novabank_kafka_dead_lettered_total{topic, group}}: records this service gave up on. Any
 * increase means an event was not applied and needs a human (fix, then replay the dead-letter
 * topic).
 */
public class DeadLetterCounter {

  private final MeterRegistry meterRegistry;
  private final String group;

  public DeadLetterCounter(MeterRegistry meterRegistry, String group) {
    this.meterRegistry = meterRegistry;
    this.group = group;
  }

  public void increment(String sourceTopic) {
    Counter.builder("novabank.kafka.dead.lettered")
        .description("Records sent to a dead-letter topic after retries, or at once if unreadable")
        .tag("topic", sourceTopic)
        .tag("group", group)
        .register(meterRegistry)
        .increment();
  }
}
