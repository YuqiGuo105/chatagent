package com.example.writer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

/**
 * A single item returned by GET /api/search.
 * Shape is compatible with the existing Portfolio search_items schema so the
 * frontend needs no changes beyond swapping the API URL.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResultDto {
    /** "blog" | "life-blog" | "project" */
    String source;
    /** "writer.blogs" | "writer.life_blogs" | "writer.projects" */
    String sourceTable;
    String sourceId;
    String title;
    String description;
    String url;
    String tags;
    String publishedAt;
    double rank;
}
