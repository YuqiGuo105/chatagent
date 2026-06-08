package com.example.writer.outbox;

import com.example.writer.model.OutboxEvent;

/**
 * Publish a single outbox event to the downstream event bus.
 * Implementations: NoOpContentEventPublisher (now), KafkaContentEventPublisher (future).
 */
public interface ContentEventPublisher {
    void publish(OutboxEvent event);
}
