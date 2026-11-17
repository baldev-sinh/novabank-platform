package com.novabank.platform.messaging.outbox;

import com.novabank.platform.messaging.MessageHeaders;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;

/**
 * {@link OutboxTracing} with Micrometer Tracing: the propagator decides the header format (W3C
 * {@code traceparent} by default, https://docs.micrometer.io/tracing/reference/api.html).
 */
public class MicrometerOutboxTracing implements OutboxTracing {

  private final Tracer tracer;
  private final Propagator propagator;

  public MicrometerOutboxTracing(Tracer tracer, Propagator propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public Map<String, String> capture() {
    Span current = tracer.currentSpan();
    if (current == null) {
      return Map.of();
    }
    Map<String, String> carrier = new LinkedHashMap<>();
    propagator.inject(current.context(), carrier, Map::put);
    return carrier;
  }

  @Override
  public PublishSpan startPublish(Map<String, String> stored, ProducerRecord<?, ?> record) {
    // No stored context (row written while nothing was traced): a new trace, not a child of
    // whatever the relay thread happens to be doing.
    Span.Builder builder =
        stored.isEmpty()
            ? tracer.spanBuilder().setNoParent()
            : propagator.extract(stored, (carrier, key) -> carrier.get(key));
    Span span =
        builder
            .name(record.topic() + " publish")
            .kind(Span.Kind.PRODUCER)
            .tag("messaging.system", "kafka")
            .tag("messaging.destination.name", record.topic())
            .start();
    propagator.inject(span.context(), record.headers(), MicrometerOutboxTracing::setHeader);
    return new MicrometerPublishSpan(span);
  }

  private static void setHeader(Headers headers, String key, String value) {
    headers.remove(key);
    headers.add(key, MessageHeaders.bytes(value));
  }

  private record MicrometerPublishSpan(Span span) implements PublishSpan {

    @Override
    public void error(Throwable failure) {
      span.error(failure);
    }

    @Override
    public void close() {
      span.end();
    }
  }
}
