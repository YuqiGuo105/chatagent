package com.example.mcp.webops.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD on the two Portfolio blog tables.
 *
 * "Blogs" (tech / portfolio blogs) — UUID primary key:
 *   id uuid, date varchar, description text, category varchar,
 *   image_url text, title varchar, content text, tags text
 *
 * life_blogs (personal / life blogs) — serial primary key:
 *   id serial, title varchar(255), image_url varchar(255),
 *   category varchar(255), published_at date, description text,
 *   require_login boolean, created_at timestamptz,
 *   updated_at timestamptz, tags varchar(255), content text
 */
@Service
@RequiredArgsConstructor
public class BlogService {

    private final JdbcTemplate jdbc;

    @Value("${webops.allow-writes:true}")
    private boolean allowWrites;

    // ----------------------------------------------------------------
    // "Blogs" table  (tech / portfolio, UUID id)
    // ----------------------------------------------------------------

    public List<Map<String, Object>> listTechBlogs() {
        return jdbc.queryForList(
                "SELECT id, title, date, category, description, tags FROM \"Blogs\" " +
                "ORDER BY date DESC NULLS LAST LIMIT 100");
    }

    public Map<String, Object> createTechBlog(String title, String date, String description,
                                               String category, String imageUrl,
                                               String content, String tags) {
        guardWrites();
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO \"Blogs\"(id, title, date, description, category, image_url, content, tags) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)",
                id, title, date, description, category, imageUrl, content, tags);
        return Map.of("created", true, "id", id, "title", title);
    }

    public Map<String, Object> updateTechBlog(String id, String title, String description,
                                               String content, String tags, String category,
                                               String imageUrl) {
        guardWrites();
        int rows = jdbc.update(
                "UPDATE \"Blogs\" SET " +
                "  title       = COALESCE(?, title), " +
                "  description = COALESCE(?, description), " +
                "  content     = COALESCE(?, content), " +
                "  tags        = COALESCE(?, tags), " +
                "  category    = COALESCE(?, category), " +
                "  image_url   = COALESCE(?, image_url) " +
                "WHERE id = ?::uuid",
                title, description, content, tags, category, imageUrl, id);
        return Map.of("updated", rows, "id", id);
    }

    public Map<String, Object> deleteTechBlog(String id) {
        guardWrites();
        int rows = jdbc.update("DELETE FROM \"Blogs\" WHERE id = ?::uuid", id);
        return Map.of("deleted", rows, "id", id);
    }

    // ----------------------------------------------------------------
    // life_blogs table  (personal, serial id)
    // ----------------------------------------------------------------

    public List<Map<String, Object>> listLifeBlogs() {
        return jdbc.queryForList(
                "SELECT id, title, category, published_at, description, " +
                "require_login, tags, created_at FROM life_blogs " +
                "ORDER BY published_at DESC NULLS LAST LIMIT 100");
    }

    public Map<String, Object> createLifeBlog(String title, String imageUrl, String category,
                                               String publishedAt, String description,
                                               Boolean requireLogin, String tags, String content) {
        guardWrites();
        // Use a query that returns the generated id
        Integer id = jdbc.queryForObject(
                "INSERT INTO life_blogs(title, image_url, category, published_at, description, " +
                "require_login, tags, content, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?::date, ?, ?, ?, ?, NOW(), NOW()) RETURNING id",
                Integer.class,
                title, imageUrl, category, publishedAt, description,
                requireLogin != null && requireLogin, tags, content);
        return Map.of("created", true, "id", id == null ? -1 : id, "title", title);
    }

    public Map<String, Object> updateLifeBlog(Integer id, String title, String description,
                                               String content, String tags, String category,
                                               String imageUrl, Boolean requireLogin) {
        guardWrites();
        int rows = jdbc.update(
                "UPDATE life_blogs SET " +
                "  title        = COALESCE(?, title), " +
                "  description  = COALESCE(?, description), " +
                "  content      = COALESCE(?, content), " +
                "  tags         = COALESCE(?, tags), " +
                "  category     = COALESCE(?, category), " +
                "  image_url    = COALESCE(?, image_url), " +
                "  require_login = COALESCE(?, require_login), " +
                "  updated_at   = NOW() " +
                "WHERE id = ?",
                title, description, content, tags, category, imageUrl, requireLogin, id);
        return Map.of("updated", rows, "id", id);
    }

    public Map<String, Object> deleteLifeBlog(Integer id) {
        guardWrites();
        int rows = jdbc.update("DELETE FROM life_blogs WHERE id = ?", id);
        return Map.of("deleted", rows, "id", id);
    }

    // ----------------------------------------------------------------
    // Analytics summary (both tables)
    // ----------------------------------------------------------------

    public Map<String, Object> analyticsSummary() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total_tech_blogs",  jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"Blogs\"", Long.class));
        r.put("total_life_blogs",  jdbc.queryForObject(
                "SELECT COUNT(*) FROM life_blogs", Long.class));
        r.put("recent_tech_blogs", jdbc.queryForList(
                "SELECT id, title, date, category FROM \"Blogs\" " +
                "ORDER BY date DESC NULLS LAST LIMIT 5"));
        r.put("recent_life_blogs", jdbc.queryForList(
                "SELECT id, title, published_at, category FROM life_blogs " +
                "ORDER BY published_at DESC NULLS LAST LIMIT 5"));
        return r;
    }

    // ----------------------------------------------------------------
    private void guardWrites() {
        if (!allowWrites) {
            throw new IllegalStateException("Write operations are disabled (webops.allow-writes=false).");
        }
    }
}
