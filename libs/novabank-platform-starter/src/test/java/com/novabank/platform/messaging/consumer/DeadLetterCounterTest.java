package com.novabank.platform.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

class DeadLetterCounterTest {

  @Test
  @DisplayName("Each dead-lettered record is counted by source topic and group, then published")
  void countsThenPublishes() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    DeadLetterPublishingRecoverer recoverer = mock(DeadLetterPublishingRecoverer.class);
    ConsumerRecordRecoverer counting =
        KafkaConsumerAutoConfiguration.counting(
            recoverer, new DeadLetterCounter(registry, "payment-service"));
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>("ledger.events.v1", 0, 7L, "k", "v");
    RuntimeException failure = new RuntimeException("boom");

    counting.accept(record, failure);
    counting.accept(record, failure);

    verify(recoverer, org.mockito.Mockito.times(2)).accept(record, failure);
    assertThat(
            registry
                .get("novabank.kafka.dead.lettered")
                .tag("topic", "ledger.events.v1")
                .tag("group", "payment-service")
                .counter()
                .count())
        .isEqualTo(2);
  }

  @Test
  @DisplayName("Without Micrometer the recoverer is used as it is")
  void noCounter() {
    DeadLetterPublishingRecoverer recoverer = mock(DeadLetterPublishingRecoverer.class);

    assertThat(KafkaConsumerAutoConfiguration.counting(recoverer, null)).isSameAs(recoverer);
  }
}
