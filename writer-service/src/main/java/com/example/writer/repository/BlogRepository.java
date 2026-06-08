package com.example.writer.repository;

import com.example.writer.model.Blog;
import com.example.writer.model.ContentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlogRepository extends JpaRepository<Blog, UUID> {
    Optional<Blog> findBySlug(String slug);
    Optional<Blog> findByIdempotencyKey(String key);
    List<Blog> findAllByStatusNot(ContentStatus status, Pageable pageable);
}
