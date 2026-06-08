package com.example.writer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "writer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AggregateType aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;  // stores "content.created" etc.

    @Builder.Default
    private Integer eventVersion = 1;

    @Column(columnDefinition = "JSONB", nullable = false)
    private String payload;  // JSON string

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    @Builder.Default
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime publishedAt;
    private String lastError;
    private String idempotencyKey;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        if (status == null) status = OutboxStatus.NEW;
    }
}
