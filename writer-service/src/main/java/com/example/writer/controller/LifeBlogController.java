package com.example.writer.controller;

import com.example.writer.dto.LifeBlogRequest;
import com.example.writer.dto.LifeBlogResponse;
import com.example.writer.service.LifeBlogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/life-blogs")
@RequiredArgsConstructor
@Validated
public class LifeBlogController {

    private final LifeBlogService lifeBlogService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LifeBlogResponse create(
            @RequestBody @Valid LifeBlogRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return lifeBlogService.create(request, idempotencyKey);
    }

    @PutMapping("/{id}")
    public LifeBlogResponse update(
            @PathVariable Long id,
            @RequestBody @Valid LifeBlogRequest request,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        return lifeBlogService.update(id, request, expectedVersion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id,
            @RequestHeader("X-Expected-Version") long expectedVersion) {
        lifeBlogService.delete(id, expectedVersion);
    }

    @GetMapping
    public Page<LifeBlogResponse> list(Pageable pageable) {
        return lifeBlogService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public LifeBlogResponse get(@PathVariable Long id) {
        return lifeBlogService.findById(id);
    }
}
