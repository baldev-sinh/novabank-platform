package com.novabank.platform.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Real OpenTelemetry + W3C propagation, so the headers are exactly what a consumer receives. */
class MicrometerOutboxTracingTest {

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final SdkTracerProvider provider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
  private final io.opentelemetry.api.trace.Tracer otelTracer = provider.get("test");
  private final Tracer tracer =
      new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
  private final OtelPropagator propagator =
      new OtelPropagator(
          ContextPropagators.create(W3CTraceContextPropagator.getInstance()), otelTracer);
  private final MicrometerOutboxTracing tracing = new MicrometerOutboxTracing(tracer, propagator);

  @AfterEach
  void close() {
    provider.close();
  }

  @Test
  @DisplayName("Nothing is captured when no span is active")
  void captureWithoutSpan() {
    assertThat(tracing.capture()).isEmpty();
  }

  @Test
  @DisplayName("The active span is captured as a W3C traceparent")
  void captureActiveSpan() {
    Span request = tracer.nextSpan().name("POST /api/v1/payments").start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
      Map<String, String> captured = tracing.capture();

      assertThat(captured.get("traceparent"))
          // version-traceId-spanId-flags; flags carry "sampled" (and W3C level 2 "random")
          .startsWith("00-" + request.context().traceId() + "-" + request.context().spanId() + "-");
    } finally {
      request.end();
    }
  }

  @Test
  @DisplayName("Publishing continues the stored trace and hands it to the consumer")
  void publishContinuesStoredTrace() {
    Span request = tracer.nextSpan().name("POST /api/v1/payments").start();
    Map<String, String> stored;
    try (Tracer.SpanInScope ignored = tracer.withSpan(request)) {
      stored = tracing.capture();
    } finally {
      request.end();
    }
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>("payment.events.v1", "key", "{}");

    // Later, on the relay thread, with no span in scope.
    try (OutboxTracing.PublishSpan ignored = tracing.startPublish(stored, producerRecord)) {
      // send
    }

    SpanData publish = lastSpan();
    assertThat(publish.getName()).isEqualTo("payment.events.v1 publish");
    assertThat(publish.getKind()).isEqualTo(SpanKind.PRODUCER);
    assertThat(publish.getTraceId()).isEqualTo(request.context().traceId());
    assertThat(publish.getParentSpanId()).isEqualTo(request.context().spanId());
    assertThat(header(producerRecord, "traceparent"))
        .startsWith("00-" + publish.getTraceId() + "-" + publish.getSpanId() + "-");
  }

  @Test
  @DisplayName("A row without trace context starts a new trace, still propagated to the consumer")
  void publishWithoutStoredContext() {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>("auth.user-events.v1", "key", "{}");

    try (OutboxTracing.PublishSpan ignored = tracing.startPublish(Map.of(), producerRecord)) {
      // send
    }

    SpanData publish = lastSpan();
    assertThat(publish.getParentSpanContext().isValid()).isFalse();
    assertThat(header(producerRecord, "traceparent")).contains(publish.getTraceId());
  }

  @Test
  @DisplayName("A failed send marks the publish span as an error")
  void failedSendIsRecorded() {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>("ledger.events.v1", "key", "{}");

    try (OutboxTracing.PublishSpan span = tracing.startPublish(Map.of(), producerRecord)) {
      span.error(new IllegalStateException("broker unavailable"));
    }

    assertThat(lastSpan().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
  }

  @Test
  @DisplayName("A stale traceparent header on the record is replaced, never duplicated")
  void replacesExistingHeader() {
    ProducerRecord<String, String> producerRecord =
        new ProducerRecord<>("account.events.v1", "key", "{}");
    producerRecord.headers().add("traceparent", "stale".getBytes(StandardCharsets.UTF_8));

    try (OutboxTracing.PublishSpan ignored = tracing.startPublish(Map.of(), producerRecord)) {
      // send
    }

    assertThat(producerRecord.headers().headers("traceparent")).hasSize(1);
    assertThat(header(producerRecord, "traceparent")).isNotEqualTo("stale");
  }

  private SpanData lastSpan() {
    var spans = exporter.getFinishedSpanItems();
    return spans.get(spans.size() - 1);
  }

  private static String header(ProducerRecord<?, ?> producerRecord, String name) {
    return new String(producerRecord.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }
}
