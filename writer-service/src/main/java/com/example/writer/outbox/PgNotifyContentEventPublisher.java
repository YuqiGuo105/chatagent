package com.example.writer.outbox;

import com.example.writer.model.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes outbox events to a Postgres NOTIFY channel.
 *
 * <p>Why a pointer payload instead of the full event:
 * <ul>
 *   <li>{@code pg_notify} payloads are capped at ~8 KB. Full blog content can exceed this.</li>
 *   <li>Keeps the channel cheap — consumers fetch the full row from {@code writer.outbox_events}
 *       by id when they need it.</li>
 * </ul>
 *
 * <p>Subscribers:
 * <ul>
 *   <li><b>Supabase Realtime</b> &mdash; expose this channel via Realtime's
 *       {@code broadcast} or {@code postgres_changes} subscription.</li>
 *   <li><b>JDBC consumers</b> &mdash; any client can {@code LISTEN writer_content_events}.</li>
 * </ul>
 *
 * <p>Activated by default. Set {@code writer.publisher.type=noop} to disable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "writer.publisher.type",
        havingValue = "pg-notify",
        matchIfMissing = true)
public class PgNotifyContentEventPublisher implements ContentEventPublisher {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${writer.publisher.pg-notify.channel:writer_content_events}")
    private String channel;

    @Override
    public void publish(OutboxEvent event) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("outbox_id", event.getId().toString());
        notification.put("event_type", event.getEventType());
        notification.put("aggregate_type", event.getAggregateType().name());
        notification.put("aggregate_id", event.getAggregateId());
        notification.put("event_version", event.getEventVersion());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize pg_notify payload", e);
        }

        // pg_notify(text, text) returns void — use execute() so we don't try to map a result.
        jdbcTemplate.execute("SELECT pg_notify(?, ?)", (PreparedStatement ps) -> {
            ps.setString(1, channel);
            ps.setString(2, payload);
            ps.execute();
            return null;
        });

        log.debug("[PgNotifyPublisher] NOTIFY {} -> {}", channel, payload);
    }
}
