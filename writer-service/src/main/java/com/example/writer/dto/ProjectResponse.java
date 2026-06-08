package com.example.writer.dto;

import com.example.writer.model.ContentStatus;
import com.example.writer.model.ContentVisibility;
import com.example.writer.model.Project;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class ProjectResponse {

    private UUID id;
    private String title;
    private String slug;
    private String description;
    private String content;
    private String tags;
    private String category;
    private String imageUrl;
    private String url;
    private String technology;
    private String year;
    private Integer num;
    private ContentStatus status;
    private ContentVisibility visibility;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .slug(project.getSlug())
                .description(project.getDescription())
                .content(project.getContent())
                .tags(project.getTags())
                .category(project.getCategory())
                .imageUrl(project.getImageUrl())
                .url(project.getUrl())
                .technology(project.getTechnology())
                .year(project.getYear())
                .num(project.getNum())
                .status(project.getStatus())
                .visibility(project.getVisibility())
                .publishedAt(project.getPublishedAt())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .version(project.getVersion())
                .build();
    }
}
