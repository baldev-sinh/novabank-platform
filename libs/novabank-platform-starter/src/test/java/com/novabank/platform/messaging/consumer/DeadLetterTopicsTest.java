package com.novabank.platform.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

class DeadLetterTopicsTest {

  private static final ConsumerRecord<String, String> RECORD =
      new ConsumerRecord<>("ledger.events.v1", 2, 41L, "key", "{}");

  @Test
  @DisplayName("One dead-letter topic per source topic and consumer group")
  void namesPerGroup() {
    assertThat(DeadLetterTopics.of("ledger.events.v1", "payment-service"))
        .isEqualTo("ledger.events.v1.payment-service-dlt");
    assertThatThrownBy(() -> DeadLetterTopics.of("ledger.events.v1", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("The failing listener's group wins over the service default; partition is kept")
  void destinationUsesFailingGroup() {
    var destination = KafkaConsumerAutoConfiguration.deadLetterDestination("account-service");

    assertThat(
            destination.apply(
                RECORD,
                new ListenerExecutionFailedException(
                    "failed", "audit-service", new RuntimeException())))
        .isEqualTo(new TopicPartition("ledger.events.v1.audit-service-dlt", 2));
    assertThat(destination.apply(RECORD, new RuntimeException()))
        .isEqualTo(new TopicPartition("ledger.events.v1.account-service-dlt", 2));
  }
}
