-- Trace context (W3C traceparent, ...) captured when the message was written, so the relay can
-- publish it in the same trace. Nullable and additive: rows written before this, or while nothing
-- was traced, simply start a new trace.
ALTER TABLE outbox_events ADD COLUMN trace_headers JSONB;
