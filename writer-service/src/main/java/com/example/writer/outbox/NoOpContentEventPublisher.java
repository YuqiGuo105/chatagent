package com.example.writer.outbox;

import com.example.writer.model.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback publisher. Activated by setting `writer.publisher.type=noop`.
 * The default publisher is {@link PgNotifyContentEventPublisher}.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "writer.publisher.type", havingValue = "noop")
public class NoOpContentEventPublisher implements ContentEventPublisher {

    @Override
    public void publish(OutboxEvent event) {
        log.info("[OutboxPublisher] NoOp — event id={} type={} aggregate={}:{}",
                event.getId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId());
    }
}
