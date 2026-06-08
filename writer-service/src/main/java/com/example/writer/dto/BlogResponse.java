package com.example.writer.dto;

import com.example.writer.model.Blog;
import com.example.writer.model.ContentStatus;
import com.example.writer.model.ContentVisibility;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class BlogResponse {

    private UUID id;
    private String title;
    private String slug;
    private String description;
    private String content;
    private String tags;
    private String category;
    private String imageUrl;
    private String url;
    private String date;
    private ContentStatus status;
    private ContentVisibility visibility;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public static BlogResponse from(Blog blog) {
        return BlogResponse.builder()
                .id(blog.getId())
                .title(blog.getTitle())
                .slug(blog.getSlug())
                .description(blog.getDescription())
                .content(blog.getContent())
                .tags(blog.getTags())
                .category(blog.getCategory())
                .imageUrl(blog.getImageUrl())
                .url(blog.getUrl())
                .date(blog.getDate())
                .status(blog.getStatus())
                .visibility(blog.getVisibility())
                .publishedAt(blog.getPublishedAt())
                .createdAt(blog.getCreatedAt())
                .updatedAt(blog.getUpdatedAt())
                .version(blog.getVersion())
                .build();
    }
}
