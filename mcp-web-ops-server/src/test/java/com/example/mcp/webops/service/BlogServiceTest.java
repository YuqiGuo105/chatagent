package com.example.mcp.webops.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BlogService}.
 *
 * Mocks {@link JdbcTemplate} to verify correct SQL and parameter passing
 * without a live database.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BlogServiceTest {

    @Mock
    JdbcTemplate jdbc;

    @InjectMocks
    BlogService blogService;

    @BeforeEach
    void setUp() {
        // Enable write operations for tests that exercise write paths
        ReflectionTestUtils.setField(blogService, "allowWrites", true);
    }

    // ── createTechBlog ───────────────────────────────────────────────────────

    @Test
    void createTechBlog_returnsCreatedTrueWithUuid() {
        // JdbcTemplate.update returns 0 by default for unmocked calls — that's fine;
        // we just verify the returned map has the right shape.
        Map<String, Object> result = blogService.createTechBlog(
                "My Title", "2025-01-01", "desc", "tech", "https://img.png", "content", "java,spring");

        assertEquals(true, result.get("created"));
        assertNotNull(result.get("id"), "Returned id must not be null");
        assertEquals("My Title", result.get("title"));
        // id must be a valid UUID
        assertDoesNotThrow(() -> java.util.UUID.fromString((String) result.get("id")));
    }

    @Test
    void createTechBlog_guardWritesFalse_throws() {
        ReflectionTestUtils.setField(blogService, "allowWrites", false);
        assertThrows(IllegalStateException.class,
                () -> blogService.createTechBlog("t", "d", "de", "c", "i", "co", "ta"));
    }

    // ── updateTechBlog ───────────────────────────────────────────────────────

    @Test
    void updateTechBlog_returnsMapWithId() {
        // jdbc.update returns 0 by default; we just verify the map structure
        String id = java.util.UUID.randomUUID().toString();
        Map<String, Object> result = blogService.updateTechBlog(id, "New", null, null, null, null, null);

        assertEquals(id, result.get("id"));
        assertNotNull(result.get("updated"));
    }

    // ── deleteTechBlog ───────────────────────────────────────────────────────

    @Test
    void deleteTechBlog_deletesById() {
        String id = java.util.UUID.randomUUID().toString();
        Map<String, Object> result = blogService.deleteTechBlog(id);
        assertEquals(id, result.get("id"));
        assertNotNull(result.get("deleted"));
    }

    // ── createLifeBlog ───────────────────────────────────────────────────────

    @Test
    void createLifeBlog_insertsAndReturnsId() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(42);

        Map<String, Object> result = blogService.createLifeBlog(
                "Life Post", "https://img.png", "life", "2025-01-01", "content", false, "tags", "long content");

        assertEquals(true, result.get("created"));
        assertEquals(42, result.get("id"));
        assertEquals("Life Post", result.get("title"));
    }

    // ── listTechBlogs ────────────────────────────────────────────────────────

    @Test
    void listTechBlogs_queriesCorrectTable() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", "abc", "title", "Test")));

        List<Map<String, Object>> rows = blogService.listTechBlogs();

        assertEquals(1, rows.size());
        verify(jdbc).queryForList(argThat(sql -> sql.contains("\"Blogs\"")));
    }

    // ── listLifeBlogs ────────────────────────────────────────────────────────

    @Test
    void listLifeBlogs_queriesCorrectTable() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        blogService.listLifeBlogs();
        verify(jdbc).queryForList(argThat(sql -> sql.contains("life_blogs")));
    }
}
