package com.example.writer.dto;

import com.example.writer.model.ContentStatus;
import com.example.writer.model.ContentVisibility;
import com.example.writer.model.LifeBlog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class LifeBlogResponse {

    private Long id;
    private String title;
    private String slug;
    private String description;
    private String content;
    private String tags;
    private String category;
    private String imageUrl;
    private String url;
    private Boolean requireLogin;
    private ContentStatus status;
    private ContentVisibility visibility;
    private LocalDate publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public static LifeBlogResponse from(LifeBlog lifeBlog) {
        return LifeBlogResponse.builder()
                .id(lifeBlog.getId())
                .title(lifeBlog.getTitle())
                .slug(lifeBlog.getSlug())
                .description(lifeBlog.getDescription())
                .content(lifeBlog.getContent())
                .tags(lifeBlog.getTags())
                .category(lifeBlog.getCategory())
                .imageUrl(lifeBlog.getImageUrl())
                .url(lifeBlog.getUrl())
                .requireLogin(lifeBlog.getRequireLogin())
                .status(lifeBlog.getStatus())
                .visibility(lifeBlog.getVisibility())
                .publishedAt(lifeBlog.getPublishedAt())
                .createdAt(lifeBlog.getCreatedAt())
                .updatedAt(lifeBlog.getUpdatedAt())
                .version(lifeBlog.getVersion())
                .build();
    }
}
