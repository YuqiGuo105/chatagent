package com.example.writer.repository;

import com.example.writer.model.LifeBlog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LifeBlogRepository extends JpaRepository<LifeBlog, Long> {
    Optional<LifeBlog> findBySlug(String slug);
    Optional<LifeBlog> findByIdempotencyKey(String key);
}
