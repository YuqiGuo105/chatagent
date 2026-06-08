package com.example.writer.controller;

import com.example.writer.dto.ProjectRequest;
import com.example.writer.dto.ProjectResponse;
import com.example.writer.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/projects")
@RequiredArgsConstructor
@Validated
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @RequestBody @Valid ProjectRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return projectService.create(request, idempotencyKey);
    }

    @PutMapping("/{id}")
    public ProjectResponse update(
            @PathVariable UUID id,
            @RequestBody @Valid ProjectRequest request,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        return projectService.update(id, request, expectedVersion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        projectService.delete(id, expectedVersion);
    }

    @GetMapping
    public Page<ProjectResponse> list(Pageable pageable) {
        return projectService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable UUID id) {
        return projectService.findById(id);
    }
}
