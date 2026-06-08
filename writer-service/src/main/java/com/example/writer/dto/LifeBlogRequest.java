package com.example.writer.dto;

import com.example.writer.model.ContentStatus;
import com.example.writer.model.ContentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LifeBlogRequest {

    @NotBlank(message = "title is required")
    private String title;

    @NotBlank(message = "slug is required")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase alphanumeric with hyphens")
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
}
