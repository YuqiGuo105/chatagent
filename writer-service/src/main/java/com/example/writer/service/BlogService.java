package com.example.writer.service;

import com.example.writer.dto.BlogRequest;
import com.example.writer.dto.BlogResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.exception.ResourceNotFoundException;
import com.example.writer.model.*;
import com.example.writer.repository.BlogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BlogService {

    private final BlogRepository blogRepository;
    private final OutboxService outboxService;

    public BlogResponse create(BlogRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Blog> existing = blogRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return BlogResponse.from(existing.get());
            }
        }

        Blog blog = Blog.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .description(request.getDescription())
                .content(request.getContent())
                .tags(request.getTags())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .url(request.getUrl())
                .date(request.getDate())
                .status(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT)
                .visibility(request.getVisibility() != null ? request.getVisibility() : ContentVisibility.PUBLIC)
                .publishedAt(request.getPublishedAt())
                .idempotencyKey(idempotencyKey)
                .build();

        blog = blogRepository.save(blog);

        outboxService.record(AggregateType.BLOG, blog.getId().toString(),
                EventType.CONTENT_CREATED, buildPayload(blog));

        return BlogResponse.from(blog);
    }

    public BlogResponse update(UUID id, BlogRequest request, long expectedVersion) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blog not found: " + id));

        if (blog.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + blog.getVersion());
        }

        blog.setTitle(request.getTitle());
        blog.setSlug(request.getSlug());
        blog.setDescription(request.getDescription());
        blog.setContent(request.getContent());
        blog.setTags(request.getTags());
        blog.setCategory(request.getCategory());
        blog.setImageUrl(request.getImageUrl());
        blog.setUrl(request.getUrl());
        blog.setDate(request.getDate());
        if (request.getStatus() != null) blog.setStatus(request.getStatus());
        if (request.getVisibility() != null) blog.setVisibility(request.getVisibility());
        if (request.getPublishedAt() != null) blog.setPublishedAt(request.getPublishedAt());

        blog = blogRepository.save(blog);

        outboxService.record(AggregateType.BLOG, blog.getId().toString(),
                EventType.CONTENT_UPDATED, buildPayload(blog));

        return BlogResponse.from(blog);
    }

    public void delete(UUID id, long expectedVersion) {
        Blog blog = blogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blog not found: " + id));

        if (blog.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + blog.getVersion());
        }

        blog.setStatus(ContentStatus.DELETED);
        blog = blogRepository.save(blog);

        outboxService.record(AggregateType.BLOG, blog.getId().toString(),
                EventType.CONTENT_DELETED, Map.of("id", blog.getId().toString(), "source", "blog"));
    }

    @Transactional(readOnly = true)
    public BlogResponse findById(UUID id) {
        return blogRepository.findById(id)
                .map(BlogResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Blog not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<BlogResponse> findAll(Pageable pageable) {
        return blogRepository.findAll(pageable).map(BlogResponse::from);
    }

    private Map<String, Object> buildPayload(Blog blog) {
        return Map.of(
                "id", blog.getId().toString(),
                "source", "blog",
                "title", blog.getTitle() != null ? blog.getTitle() : "",
                "description", blog.getDescription() != null ? blog.getDescription() : "",
                "tags", blog.getTags() != null ? blog.getTags() : "",
                "content", blog.getContent() != null ? blog.getContent() : "",
                "url", blog.getUrl() != null ? blog.getUrl() : "/blog-single/" + blog.getId(),
                "published_at", blog.getPublishedAt() != null ? blog.getPublishedAt().toString() : ""
        );
    }
}
