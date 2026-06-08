package com.example.writer.service;

import com.example.writer.dto.LifeBlogRequest;
import com.example.writer.dto.LifeBlogResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.exception.ResourceNotFoundException;
import com.example.writer.model.*;
import com.example.writer.repository.LifeBlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class LifeBlogService {

    private final LifeBlogRepository lifeBlogRepository;
    private final OutboxService outboxService;

    public LifeBlogResponse create(LifeBlogRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<LifeBlog> existing = lifeBlogRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return LifeBlogResponse.from(existing.get());
            }
        }

        LifeBlog lifeBlog = LifeBlog.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .description(request.getDescription())
                .content(request.getContent())
                .tags(request.getTags())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .url(request.getUrl())
                .requireLogin(request.getRequireLogin() != null ? request.getRequireLogin() : false)
                .status(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT)
                .visibility(request.getVisibility() != null ? request.getVisibility() : ContentVisibility.PUBLIC)
                .publishedAt(request.getPublishedAt())
                .idempotencyKey(idempotencyKey)
                .build();

        lifeBlog = lifeBlogRepository.save(lifeBlog);

        outboxService.record(AggregateType.LIFE_BLOG, lifeBlog.getId().toString(),
                EventType.CONTENT_CREATED, buildPayload(lifeBlog));

        return LifeBlogResponse.from(lifeBlog);
    }

    public LifeBlogResponse update(Long id, LifeBlogRequest request, long expectedVersion) {
        LifeBlog lifeBlog = lifeBlogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LifeBlog not found: " + id));

        if (lifeBlog.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + lifeBlog.getVersion());
        }

        lifeBlog.setTitle(request.getTitle());
        lifeBlog.setSlug(request.getSlug());
        lifeBlog.setDescription(request.getDescription());
        lifeBlog.setContent(request.getContent());
        lifeBlog.setTags(request.getTags());
        lifeBlog.setCategory(request.getCategory());
        lifeBlog.setImageUrl(request.getImageUrl());
        lifeBlog.setUrl(request.getUrl());
        if (request.getRequireLogin() != null) lifeBlog.setRequireLogin(request.getRequireLogin());
        if (request.getStatus() != null) lifeBlog.setStatus(request.getStatus());
        if (request.getVisibility() != null) lifeBlog.setVisibility(request.getVisibility());
        if (request.getPublishedAt() != null) lifeBlog.setPublishedAt(request.getPublishedAt());

        lifeBlog = lifeBlogRepository.save(lifeBlog);

        outboxService.record(AggregateType.LIFE_BLOG, lifeBlog.getId().toString(),
                EventType.CONTENT_UPDATED, buildPayload(lifeBlog));

        return LifeBlogResponse.from(lifeBlog);
    }

    public void delete(Long id, long expectedVersion) {
        LifeBlog lifeBlog = lifeBlogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LifeBlog not found: " + id));

        if (lifeBlog.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + lifeBlog.getVersion());
        }

        lifeBlog.setStatus(ContentStatus.DELETED);
        lifeBlog = lifeBlogRepository.save(lifeBlog);

        outboxService.record(AggregateType.LIFE_BLOG, lifeBlog.getId().toString(),
                EventType.CONTENT_DELETED, Map.of("id", lifeBlog.getId().toString(), "source", "life_blog"));
    }

    @Transactional(readOnly = true)
    public LifeBlogResponse findById(Long id) {
        return lifeBlogRepository.findById(id)
                .map(LifeBlogResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("LifeBlog not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<LifeBlogResponse> findAll(Pageable pageable) {
        return lifeBlogRepository.findAll(pageable).map(LifeBlogResponse::from);
    }

    private Map<String, Object> buildPayload(LifeBlog lifeBlog) {
        return Map.of(
                "id", lifeBlog.getId().toString(),
                "source", "life_blog",
                "title", lifeBlog.getTitle() != null ? lifeBlog.getTitle() : "",
                "description", lifeBlog.getDescription() != null ? lifeBlog.getDescription() : "",
                "tags", lifeBlog.getTags() != null ? lifeBlog.getTags() : "",
                "content", lifeBlog.getContent() != null ? lifeBlog.getContent() : "",
                "url", lifeBlog.getUrl() != null ? lifeBlog.getUrl() : "/life-blog/" + lifeBlog.getId(),
                "published_at", lifeBlog.getPublishedAt() != null ? lifeBlog.getPublishedAt().toString() : ""
        );
    }
}
