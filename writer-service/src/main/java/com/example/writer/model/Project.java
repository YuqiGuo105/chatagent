package com.example.writer.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "projects", schema = "writer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String tags;
    private String category;
    private String imageUrl;
    private String url;
    private String technology;
    private String year;
    private Integer num;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentVisibility visibility;

    private OffsetDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    private String createdBy;
    private String updatedBy;

    @Version
    private Long version;

    @Column(unique = true)
    private String idempotencyKey;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
        if (status == null) status = ContentStatus.DRAFT;
        if (visibility == null) visibility = ContentVisibility.PUBLIC;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
