package com.example.writer.controller;

import com.example.writer.dto.BlogRequest;
import com.example.writer.dto.BlogResponse;
import com.example.writer.service.BlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/blogs")
@RequiredArgsConstructor
@Validated
public class BlogController {

    private final BlogService blogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlogResponse create(
            @RequestBody @Valid BlogRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return blogService.create(request, idempotencyKey);
    }

    @PutMapping("/{id}")
    public BlogResponse update(
            @PathVariable UUID id,
            @RequestBody @Valid BlogRequest request,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        return blogService.update(id, request, expectedVersion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        blogService.delete(id, expectedVersion);
    }

    @GetMapping
    public Page<BlogResponse> list(Pageable pageable) {
        return blogService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public BlogResponse get(@PathVariable UUID id) {
        return blogService.findById(id);
    }
}
