package com.novabank.platform.messaging.outbox;

import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * Carries the trace across the outbox. The relay publishes later, on its own thread, so without
 * this every message would start a new trace and a payment could not be followed from the HTTP
 * request through the services it touches.
 */
public interface OutboxTracing {

  /** No tracer on the classpath: nothing is stored and nothing is added to the records. */
  OutboxTracing NONE =
      new OutboxTracing() {
        @Override
        public Map<String, String> capture() {
          return Map.of();
        }

        @Override
        public PublishSpan startPublish(Map<String, String> stored, ProducerRecord<?, ?> record) {
          return PublishSpan.NONE;
        }
      };

  /**
   * The current trace context as propagation headers (W3C {@code traceparent}, ...), stored with
   * the outbox row. Empty when nothing is being traced.
   */
  Map<String, String> capture();

  /**
   * Starts a producer span that continues the stored context and writes it into the record's
   * headers, so the consumer joins the same trace.
   */
  PublishSpan startPublish(Map<String, String> stored, ProducerRecord<?, ?> record);

  /** Ends the publish span; record the failure first if the send failed. */
  interface PublishSpan extends AutoCloseable {

    PublishSpan NONE =
        new PublishSpan() {
          @Override
          public void error(Throwable failure) {}

          @Override
          public void close() {}
        };

    void error(Throwable failure);

    @Override
    void close();
  }
}
