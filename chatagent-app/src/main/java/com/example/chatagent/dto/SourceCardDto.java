package com.example.chatagent.dto;

/**
 * Lightweight DTO sent to the frontend as part of the {@code sources_found} SSE event.
 * Carries everything the ChatWidget needs to render a clickable source card.
 *
 * @param id         Source record id (string — may be integer or UUID)
 * @param type       One of: {@code life_blog}, {@code tech_blog}, {@code project}
 * @param title      Human-readable title
 * @param imageUrl   Cover image URL (may be null)
 * @param url        Frontend path, e.g. {@code /life-blog/2} or {@code /work-single/<uuid>}
 * @param publishedAt ISO date string (may be null for projects)
 */
public record SourceCardDto(
        String id,
        String type,
        String title,
        String imageUrl,
        String url,
        String publishedAt
) {}
