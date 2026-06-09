package com.example.writer.dto;

import java.util.List;

/**
 * Response envelope for GET /api/search — mirrors the shape returned by
 * Portfolio's existing /api/search so SearchOverlay.js works without changes.
 */
public record SearchResponseDto(
        List<SearchResultDto> results,
        long total,
        int limit,
        int offset
) {}
