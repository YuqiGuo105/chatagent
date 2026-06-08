package com.example.writer.service;

import com.example.writer.dto.ProjectRequest;
import com.example.writer.dto.ProjectResponse;
import com.example.writer.exception.OptimisticLockConflictException;
import com.example.writer.exception.ResourceNotFoundException;
import com.example.writer.model.*;
import com.example.writer.repository.ProjectRepository;
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
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OutboxService outboxService;

    public ProjectResponse create(ProjectRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Project> existing = projectRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return ProjectResponse.from(existing.get());
            }
        }

        Project project = Project.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .description(request.getDescription())
                .content(request.getContent())
                .tags(request.getTags())
                .category(request.getCategory())
                .imageUrl(request.getImageUrl())
                .url(request.getUrl())
                .technology(request.getTechnology())
                .year(request.getYear())
                .num(request.getNum())
                .status(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT)
                .visibility(request.getVisibility() != null ? request.getVisibility() : ContentVisibility.PUBLIC)
                .publishedAt(request.getPublishedAt())
                .idempotencyKey(idempotencyKey)
                .build();

        project = projectRepository.save(project);

        outboxService.record(AggregateType.PROJECT, project.getId().toString(),
                EventType.CONTENT_CREATED, buildPayload(project));

        return ProjectResponse.from(project);
    }

    public ProjectResponse update(UUID id, ProjectRequest request, long expectedVersion) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (project.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + project.getVersion());
        }

        project.setTitle(request.getTitle());
        project.setSlug(request.getSlug());
        project.setDescription(request.getDescription());
        project.setContent(request.getContent());
        project.setTags(request.getTags());
        project.setCategory(request.getCategory());
        project.setImageUrl(request.getImageUrl());
        project.setUrl(request.getUrl());
        project.setTechnology(request.getTechnology());
        project.setYear(request.getYear());
        project.setNum(request.getNum());
        if (request.getStatus() != null) project.setStatus(request.getStatus());
        if (request.getVisibility() != null) project.setVisibility(request.getVisibility());
        if (request.getPublishedAt() != null) project.setPublishedAt(request.getPublishedAt());

        project = projectRepository.save(project);

        outboxService.record(AggregateType.PROJECT, project.getId().toString(),
                EventType.CONTENT_UPDATED, buildPayload(project));

        return ProjectResponse.from(project);
    }

    public void delete(UUID id, long expectedVersion) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        if (project.getVersion() != expectedVersion) {
            throw new OptimisticLockConflictException(
                    "Version conflict: expected " + expectedVersion + ", actual " + project.getVersion());
        }

        project.setStatus(ContentStatus.DELETED);
        project = projectRepository.save(project);

        outboxService.record(AggregateType.PROJECT, project.getId().toString(),
                EventType.CONTENT_DELETED, Map.of("id", project.getId().toString(), "source", "project"));
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        return projectRepository.findById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> findAll(Pageable pageable) {
        return projectRepository.findAll(pageable).map(ProjectResponse::from);
    }

    private Map<String, Object> buildPayload(Project project) {
        return Map.of(
                "id", project.getId().toString(),
                "source", "project",
                "title", project.getTitle() != null ? project.getTitle() : "",
                "description", project.getDescription() != null ? project.getDescription() : "",
                "tags", project.getTags() != null ? project.getTags() : "",
                "content", project.getContent() != null ? project.getContent() : "",
                "url", project.getUrl() != null ? project.getUrl() : "/work-single/" + project.getId(),
                "published_at", project.getPublishedAt() != null ? project.getPublishedAt().toString() : ""
        );
    }
}
