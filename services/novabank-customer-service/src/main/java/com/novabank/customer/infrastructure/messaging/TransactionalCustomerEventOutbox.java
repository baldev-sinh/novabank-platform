package com.novabank.customer.infrastructure.messaging;

import com.novabank.customer.application.event.CustomerVerifiedEvent;
import com.novabank.customer.application.port.messaging.CustomerEventOutbox;
import com.novabank.platform.messaging.outbox.OutboxRecord;
import com.novabank.platform.messaging.outbox.OutboxWriter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.stereotype.Component;

/** Adapter from the application's {@link CustomerEventOutbox} port to the platform outbox. */
@Component
public class TransactionalCustomerEventOutbox implements CustomerEventOutbox {

  private final OutboxWriter outboxWriter;
  private final CustomerEventsProperties customerEvents;

  public TransactionalCustomerEventOutbox(
      OutboxWriter outboxWriter, CustomerEventsProperties customerEvents) {
    this.outboxWriter = outboxWriter;
    this.customerEvents = customerEvents;
  }

  /** The writer requires an active transaction: the event commits with the business change. */
  @Override
  public void add(CustomerVerifiedEvent event) {
    outboxWriter.append(
        new OutboxRecord(
            event.eventId(),
            customerEvents.topic(),
            CustomerVerifiedEvent.TYPE,
            CustomerVerifiedEvent.AGGREGATE_TYPE,
            event.customerId(),
            event,
            event.occurredAt()));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(CustomerEventsProperties.class)
  static class TopicConfiguration {

    /** Created on startup if missing (Kafka auto-creation is off on purpose). */
    @Bean
    NewTopic customerEventsTopic(CustomerEventsProperties properties) {
      return TopicBuilder.name(properties.topic())
          .partitions(properties.partitions())
          .replicas(properties.replicationFactor())
          .build();
    }
  }
}
